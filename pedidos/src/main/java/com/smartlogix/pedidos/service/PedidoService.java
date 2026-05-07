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
    private final String INVENTARIO_URL = "http://localhost:8081/api/inventario/productos";

    public List<Pedido> obtenerTodos() {
        return pedidoRepository.findAll();
    }

    @CircuitBreaker(name = "inventarioCB", fallbackMethod = "fallbackCrearPedido")
    public Pedido crearPedido(Pedido pedido) {
        // 1. Traemos todo el inventario
        ProductoDTO[] inventario = restTemplate.getForObject(INVENTARIO_URL, ProductoDTO[].class);
        
        boolean todosConStock = true;

        // 2. Verificamos cada item del pedido contra el inventario
        if (inventario != null && pedido.getItems() != null) {
            for (ItemPedido item : pedido.getItems()) {
                boolean itemTieneStock = false;
                
                for (ProductoDTO productoInv : inventario) {
                    if (productoInv.getSku().equals(item.getSkuProducto()) && productoInv.getStock() >= item.getCantidad()) {
                        itemTieneStock = true;
                        break;
                    }
                }
                
                // Si al menos un producto del carrito no tiene stock, rechazamos todo el pedido
                if (!itemTieneStock) {
                    todosConStock = false;
                    break;
                }
            }
        } else {
            todosConStock = false; // Si no hay respuesta del inventario o el carrito está vacío
        }

        // 3. Asignamos estado
        if (todosConStock) {
            pedido.setEstado("APROBADO");
        } else {
            pedido.setEstado("RECHAZADO_FALTA_STOCK");
        }

        // 4. Guardamos el pedido (esto guardará el Pedido y todos sus ItemPedido gracias al Cascade)
        return pedidoRepository.save(pedido);
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
                pedidoExistente.getItems().clear();
                pedidoExistente.getItems().addAll(pedidoDetalles.getItems());
                
                // 3. Validamos stock de los nuevos productos llamando al MS-Inventario
                ProductoDTO[] inventario = restTemplate.getForObject(INVENTARIO_URL, ProductoDTO[].class);
                boolean stockOk = true;
                
                for (ItemPedido item : pedidoExistente.getItems()) {
                    boolean encontrado = false;
                    for (ProductoDTO p : inventario) {
                        if (p.getSku().equals(item.getSkuProducto()) && p.getStock() >= item.getCantidad()) {
                            encontrado = true;
                            break;
                        }
                    }
                    if (!encontrado) { stockOk = false; break; }
                }

                if (!stockOk) {
                    pedidoExistente.setEstado("RECHAZADO_FALTA_STOCK_EN_EDICION");
                }
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