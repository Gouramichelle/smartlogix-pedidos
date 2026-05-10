package com.smartlogix.pedidos.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.smartlogix.pedidos.model.ItemPedido;
import com.smartlogix.pedidos.model.Pedido;
import com.smartlogix.pedidos.model.ProductoDTO;
import com.smartlogix.pedidos.repository.PedidoRepository;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final RestTemplate restTemplate;

    // URL fija del MS-Inventario (En un entorno real usaríamos Eureka o Kubernetes DNS)
    private final String INVENTARIO_URL = "http://localhost:8085/api/inventario/productos";

    public List<Pedido> obtenerTodos() {
        return pedidoRepository.findAll();
    }

    public java.util.Optional<Pedido> obtenerPedidoPorId(Long id) {
        return pedidoRepository.findById(id);
    }

    @CircuitBreaker(name = "inventarioCB", fallbackMethod = "fallbackCrearPedido")
    public Pedido crearPedido(Pedido pedido) {
        // 1. Traemos todo el inventario
        ProductoDTO[] inventario = restTemplate.getForObject(INVENTARIO_URL, ProductoDTO[].class);
        
        boolean todosConStock = true;

        // 2. Agrupamos las cantidades por SKU
        java.util.Map<String, Integer> cantidadesPorSku = new java.util.HashMap<>();
        if (pedido.getItems() != null) {
            for (ItemPedido item : pedido.getItems()) {
                cantidadesPorSku.put(item.getSkuProducto(), cantidadesPorSku.getOrDefault(item.getSkuProducto(), 0) + item.getCantidad());
            }
        }

        // 3. Verificamos cada SKU agrupado contra el inventario
        if (inventario != null && !cantidadesPorSku.isEmpty()) {
            for (java.util.Map.Entry<String, Integer> entry : cantidadesPorSku.entrySet()) {
                String sku = entry.getKey();
                Integer cantidadTotal = entry.getValue();
                boolean skuTieneStock = false;
                
                for (ProductoDTO productoInv : inventario) {
                    if (productoInv.getSku().equals(sku) && productoInv.getStock() >= cantidadTotal) {
                        skuTieneStock = true;
                        break;
                    }
                }
                
                // Si al menos un SKU no tiene stock suficiente, rechazamos todo el pedido
                if (!skuTieneStock) {
                    todosConStock = false;
                    break;
                }
            }
        } else {
            todosConStock = false; // Si no hay respuesta del inventario o el carrito está vacío
        }

        // 4. Asignamos estado
        if (todosConStock) {
            pedido.setEstado("APROBADO");
        } else {
            pedido.setEstado("RECHAZADO_FALTA_STOCK");
        }

        // 5. Guardamos el pedido (esto guardará el Pedido y todos sus ItemPedido gracias al Cascade)
        Pedido pedidoGuardado = pedidoRepository.save(pedido);

        // 6. Si el pedido fue aprobado, descontamos el stock en MS-Inventario
        if ("APROBADO".equals(pedidoGuardado.getEstado())) {
            for (java.util.Map.Entry<String, Integer> entry : cantidadesPorSku.entrySet()) {
                String sku = entry.getKey();
                Integer cantidadTotal = entry.getValue();
                try {
                    // Obtener el producto por SKU
                    ProductoDTO producto = restTemplate.getForObject(INVENTARIO_URL + "/sku/" + sku, ProductoDTO.class);
                    if (producto != null && producto.getStock() >= cantidadTotal) {
                        // Descontar stock
                        producto.setStock(producto.getStock() - cantidadTotal);
                        // Actualizar en MS-Inventario
                        restTemplate.put(INVENTARIO_URL + "/" + producto.getId(), producto);
                        System.out.println("Stock descontado para SKU " + sku + ": " + cantidadTotal + ", stock restante: " + producto.getStock());
                    } else {
                        System.err.println("Error: stock insuficiente para SKU " + sku);
                    }
                } catch (Exception e) {
                    // Si falla el descuento, podríamos marcar como pendiente, pero por ahora logueamos
                    System.err.println("Error descontando stock para SKU " + sku + ": " + e.getMessage());
                }
            }
        }

        return pedidoGuardado;
    }

    // MÉTODO FALLBACK: Se ejecuta automáticamente si el MS-Inventario está caído (Puerto 8081 apagado)
    public Pedido fallbackCrearPedido(Pedido pedido, Throwable exception) {
        pedido.setEstado("PENDIENTE_POR_FALLA_SISTEMA");
        // Lo guardamos como pendiente para procesarlo cuando el inventario vuelva a la vida
        return pedidoRepository.save(pedido);
    }
    @CircuitBreaker(name = "inventarioCB", fallbackMethod = "fallbackCrearPedido")
    public Pedido actualizarPedido(Long id, Pedido pedidoDetalles) {
        return pedidoRepository.findById(id).map(pedidoExistente -> {
            
            // 1. Actualizamos el estado
            pedidoExistente.setEstado(pedidoDetalles.getEstado());

            // 2. Si se envían nuevos items, reemplazamos la lista anterior
            // Gracias a 'orphanRemoval = true', JPA borrará los items viejos en la BD automáticamente
            if (pedidoDetalles.getItems() != null) {
                java.util.Map<String, Integer> stockAntiguoPorSku = new java.util.HashMap<>();
                for (ItemPedido item : pedidoExistente.getItems()) {
                    stockAntiguoPorSku.put(item.getSkuProducto(), stockAntiguoPorSku.getOrDefault(item.getSkuProducto(), 0) + item.getCantidad());
                }

                java.util.Map<String, Integer> stockNuevoPorSku = new java.util.HashMap<>();
                for (ItemPedido item : pedidoDetalles.getItems()) {
                    stockNuevoPorSku.put(item.getSkuProducto(), stockNuevoPorSku.getOrDefault(item.getSkuProducto(), 0) + item.getCantidad());
                }

                java.util.Map<String, Integer> diferenciaPorSku = new java.util.HashMap<>();
                for (String sku : stockAntiguoPorSku.keySet()) {
                    diferenciaPorSku.put(sku, stockNuevoPorSku.getOrDefault(sku, 0) - stockAntiguoPorSku.getOrDefault(sku, 0));
                }
                for (String sku : stockNuevoPorSku.keySet()) {
                    diferenciaPorSku.putIfAbsent(sku, stockNuevoPorSku.get(sku) - stockAntiguoPorSku.getOrDefault(sku, 0));
                }

                ProductoDTO[] inventario = restTemplate.getForObject(INVENTARIO_URL, ProductoDTO[].class);
                boolean stockOk = true;

                if (inventario != null) {
                    for (java.util.Map.Entry<String, Integer> entry : diferenciaPorSku.entrySet()) {
                        int diff = entry.getValue();
                        if (diff > 0) {
                            boolean encontrado = false;
                            for (ProductoDTO p : inventario) {
                                if (p.getSku().equals(entry.getKey()) && p.getStock() >= diff) {
                                    encontrado = true;
                                    break;
                                }
                            }
                            if (!encontrado) {
                                stockOk = false;
                                break;
                            }
                        }
                    }
                } else {
                    stockOk = false;
                }

                if (!stockOk) {
                    pedidoExistente.setEstado("RECHAZADO_FALTA_STOCK_EN_EDICION");
                } else {
                    // Ajustamos stock en inventario según la diferencia de cantidades
                    for (java.util.Map.Entry<String, Integer> entry : diferenciaPorSku.entrySet()) {
                        String sku = entry.getKey();
                        int diff = entry.getValue();
                        if (diff == 0) continue;
                        try {
                            ProductoDTO producto = restTemplate.getForObject(INVENTARIO_URL + "/sku/" + sku, ProductoDTO.class);
                            if (producto != null) {
                                producto.setStock(producto.getStock() - diff);
                                restTemplate.put(INVENTARIO_URL + "/" + producto.getId(), producto);
                            }
                        } catch (Exception e) {
                            System.err.println("Error ajustando stock para SKU " + sku + ": " + e.getMessage());
                        }
                    }
                }

                pedidoExistente.getItems().clear();
                pedidoExistente.getItems().addAll(pedidoDetalles.getItems());
            }

            return pedidoRepository.save(pedidoExistente);
        }).orElseThrow(() -> new RuntimeException("No se encontró el pedido con ID: " + id));
    }

    // Método para eliminar un pedido
    public void eliminarPedido(Long id) {
        if(pedidoRepository.existsById(id)){
            pedidoRepository.deleteById(id);
        } else {
            throw new RuntimeException("Pedido no encontrado con el ID: " + id);
        }
    }
}