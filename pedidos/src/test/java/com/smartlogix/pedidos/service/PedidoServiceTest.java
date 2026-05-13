package com.smartlogix.pedidos.service;

import com.smartlogix.pedidos.model.ItemPedido;
import com.smartlogix.pedidos.model.Pedido;
import com.smartlogix.pedidos.model.ProductoDTO;
import com.smartlogix.pedidos.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    private static final String INVENTARIO_URL = "http://localhost:8085/api/inventario/productos";

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private PedidoService pedidoService;

    private Pedido pedido;
    private ProductoDTO productoInventario;

    @BeforeEach
    void setUp() {
        ItemPedido item = new ItemPedido();
        item.setSkuProducto("SKU-001");
        item.setCantidad(2);

        pedido = new Pedido();
        pedido.setId(1L);
        pedido.setCliente("Cliente Web");
        pedido.setEstado("PENDIENTE");
        pedido.setItems(new ArrayList<>(List.of(item)));

        productoInventario = new ProductoDTO();
        productoInventario.setId(10L);
        productoInventario.setSku("SKU-001");
        productoInventario.setStock(10);
        productoInventario.setPrecio(9.99);
    }

    // ── obtenerTodos ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("obtenerTodos() retorna lista de pedidos")
    void obtenerTodos_retornaLista() {
        when(pedidoRepository.findAll()).thenReturn(List.of(pedido));

        var resultado = pedidoService.obtenerTodos();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getCliente()).isEqualTo("Cliente Web");
        verify(pedidoRepository).findAll();
    }

    @Test
    @DisplayName("obtenerTodos() retorna lista vacía cuando no hay pedidos")
    void obtenerTodos_sinPedidos_retornaVacio() {
        when(pedidoRepository.findAll()).thenReturn(List.of());

        assertThat(pedidoService.obtenerTodos()).isEmpty();
    }

    // ── obtenerPedidoPorId ──────────────────────────────────────────────────────

    @Test
    @DisplayName("obtenerPedidoPorId() retorna el pedido si existe")
    void obtenerPedidoPorId_existente_retornaPedido() {
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));

        var resultado = pedidoService.obtenerPedidoPorId(1L);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("obtenerPedidoPorId() retorna Optional vacío si no existe")
    void obtenerPedidoPorId_noExistente_retornaVacio() {
        when(pedidoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(pedidoService.obtenerPedidoPorId(99L)).isEmpty();
    }

    // ── crearPedido ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("crearPedido() asigna APROBADO cuando hay stock suficiente y descuenta inventario")
    void crearPedido_conStockSuficiente_retornaAprobado() {
        when(restTemplate.getForObject(INVENTARIO_URL, ProductoDTO[].class))
                .thenReturn(new ProductoDTO[]{productoInventario});
        when(restTemplate.getForObject(INVENTARIO_URL + "/sku/SKU-001", ProductoDTO.class))
                .thenReturn(productoInventario);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido resultado = pedidoService.crearPedido(pedido);

        assertThat(resultado.getEstado()).isEqualTo("APROBADO");
        verify(pedidoRepository).save(pedido);
        verify(restTemplate).put(eq(INVENTARIO_URL + "/10"), any(ProductoDTO.class));
    }

    @Test
    @DisplayName("crearPedido() asigna RECHAZADO_FALTA_STOCK cuando el stock es insuficiente")
    void crearPedido_sinStockSuficiente_retornaRechazado() {
        productoInventario.setStock(1); // necesita 2, solo hay 1
        when(restTemplate.getForObject(INVENTARIO_URL, ProductoDTO[].class))
                .thenReturn(new ProductoDTO[]{productoInventario});
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido resultado = pedidoService.crearPedido(pedido);

        assertThat(resultado.getEstado()).isEqualTo("RECHAZADO_FALTA_STOCK");
        verify(restTemplate, never()).put(anyString(), any());
    }

    @Test
    @DisplayName("crearPedido() asigna RECHAZADO_FALTA_STOCK cuando el inventario no responde (null)")
    void crearPedido_inventarioNull_retornaRechazado() {
        when(restTemplate.getForObject(INVENTARIO_URL, ProductoDTO[].class)).thenReturn(null);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido resultado = pedidoService.crearPedido(pedido);

        assertThat(resultado.getEstado()).isEqualTo("RECHAZADO_FALTA_STOCK");
    }

    @Test
    @DisplayName("crearPedido() asigna RECHAZADO_FALTA_STOCK cuando el pedido no tiene items")
    void crearPedido_sinItems_retornaRechazado() {
        pedido.setItems(new ArrayList<>());
        when(restTemplate.getForObject(INVENTARIO_URL, ProductoDTO[].class))
                .thenReturn(new ProductoDTO[]{productoInventario});
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido resultado = pedidoService.crearPedido(pedido);

        assertThat(resultado.getEstado()).isEqualTo("RECHAZADO_FALTA_STOCK");
    }

    @Test
    @DisplayName("crearPedido() asigna RECHAZADO_FALTA_STOCK cuando items es null")
    void crearPedido_itemsNull_retornaRechazado() {
        pedido.setItems(null);
        when(restTemplate.getForObject(INVENTARIO_URL, ProductoDTO[].class))
                .thenReturn(new ProductoDTO[]{productoInventario});
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido resultado = pedidoService.crearPedido(pedido);

        assertThat(resultado.getEstado()).isEqualTo("RECHAZADO_FALTA_STOCK");
    }

    // ── fallbackCrearPedido ─────────────────────────────────────────────────────

    @Test
    @DisplayName("fallbackCrearPedido() asigna estado PENDIENTE_POR_FALLA_SISTEMA")
    void fallback_asignaEstadoPendiente() {
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        var resultado = pedidoService.fallbackCrearPedido(
                pedido,
                new RuntimeException("MS-Inventario no disponible"));

        assertThat(resultado.getEstado()).isEqualTo("PENDIENTE_POR_FALLA_SISTEMA");
        verify(pedidoRepository).save(pedido);
    }

    // ── actualizarPedido ────────────────────────────────────────────────────────

    @Test
    @DisplayName("actualizarPedido() lanza excepción si el pedido no existe")
    void actualizarPedido_pedidoNoExiste_lanzaExcepcion() {
        when(pedidoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pedidoService.actualizarPedido(99L, pedido))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("actualizarPedido() solo actualiza el estado cuando no se envían nuevos items")
    void actualizarPedido_sinNuevosItems_actualizaSoloEstado() {
        Pedido existente = pedidoConItems("SKU-001", 2, "PENDIENTE");
        Pedido detalles = new Pedido();
        detalles.setEstado("CANCELADO");
        detalles.setItems(null);

        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido resultado = pedidoService.actualizarPedido(1L, detalles);

        assertThat(resultado.getEstado()).isEqualTo("CANCELADO");
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("actualizarPedido() actualiza items y descuenta stock cuando hay suficiente inventario")
    void actualizarPedido_conStockSuficiente_actualizaItems() {
        Pedido existente = pedidoConItems("SKU-001", 1, "PENDIENTE");
        Pedido detalles = pedidoConItems("SKU-001", 3, "APROBADO"); // diff = +2

        when(restTemplate.getForObject(INVENTARIO_URL, ProductoDTO[].class))
                .thenReturn(new ProductoDTO[]{productoInventario}); // stock=10 >= diff=2
        when(restTemplate.getForObject(INVENTARIO_URL + "/sku/SKU-001", ProductoDTO.class))
                .thenReturn(productoInventario);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido resultado = pedidoService.actualizarPedido(1L, detalles);

        assertThat(resultado.getEstado()).isEqualTo("APROBADO");
        assertThat(resultado.getItems()).hasSize(1);
        assertThat(resultado.getItems().get(0).getCantidad()).isEqualTo(3);
        verify(restTemplate).put(eq(INVENTARIO_URL + "/10"), any(ProductoDTO.class));
    }

    @Test
    @DisplayName("actualizarPedido() asigna RECHAZADO_FALTA_STOCK_EN_EDICION cuando el stock es insuficiente")
    void actualizarPedido_sinStockSuficiente_retornaRechazadoEnEdicion() {
        Pedido existente = pedidoConItems("SKU-001", 1, "APROBADO");
        Pedido detalles = pedidoConItems("SKU-001", 5, "APROBADO"); // diff = +4

        productoInventario.setStock(2); // stock=2 < diff=4
        when(restTemplate.getForObject(INVENTARIO_URL, ProductoDTO[].class))
                .thenReturn(new ProductoDTO[]{productoInventario});
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido resultado = pedidoService.actualizarPedido(1L, detalles);

        assertThat(resultado.getEstado()).isEqualTo("RECHAZADO_FALTA_STOCK_EN_EDICION");
        verify(restTemplate, never()).put(anyString(), any());
    }

    @Test
    @DisplayName("actualizarPedido() asigna RECHAZADO_FALTA_STOCK_EN_EDICION cuando el inventario devuelve null")
    void actualizarPedido_inventarioNull_retornaRechazadoEnEdicion() {
        Pedido existente = pedidoConItems("SKU-001", 1, "APROBADO");
        Pedido detalles = pedidoConItems("SKU-001", 3, "APROBADO");

        when(restTemplate.getForObject(INVENTARIO_URL, ProductoDTO[].class)).thenReturn(null);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido resultado = pedidoService.actualizarPedido(1L, detalles);

        assertThat(resultado.getEstado()).isEqualTo("RECHAZADO_FALTA_STOCK_EN_EDICION");
    }

    @Test
    @DisplayName("actualizarPedido() devuelve stock al inventario cuando se reduce la cantidad (diff negativo)")
    void actualizarPedido_reduccionCantidad_devuelveStock() {
        Pedido existente = pedidoConItems("SKU-001", 5, "APROBADO");
        Pedido detalles = pedidoConItems("SKU-001", 2, "APROBADO"); // diff = -3 (devuelve stock)

        when(restTemplate.getForObject(INVENTARIO_URL, ProductoDTO[].class))
                .thenReturn(new ProductoDTO[]{productoInventario});
        when(restTemplate.getForObject(INVENTARIO_URL + "/sku/SKU-001", ProductoDTO.class))
                .thenReturn(productoInventario);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido resultado = pedidoService.actualizarPedido(1L, detalles);

        assertThat(resultado.getEstado()).isEqualTo("APROBADO");
        verify(restTemplate).put(eq(INVENTARIO_URL + "/10"), any(ProductoDTO.class));
    }

    // ── eliminarPedido ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("eliminarPedido() lanza excepción si el pedido no existe")
    void eliminarPedido_noExiste_lanzaExcepcion() {
        when(pedidoRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> pedidoService.eliminarPedido(99L))
                .isInstanceOf(RuntimeException.class);

        verify(pedidoRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("eliminarPedido() invoca deleteById si el pedido existe")
    void eliminarPedido_existente_invocaDelete() {
        when(pedidoRepository.existsById(1L)).thenReturn(true);
        doNothing().when(pedidoRepository).deleteById(1L);

        pedidoService.eliminarPedido(1L);

        verify(pedidoRepository, times(1)).deleteById(1L);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private Pedido pedidoConItems(String sku, int cantidad, String estado) {
        ItemPedido item = new ItemPedido();
        item.setSkuProducto(sku);
        item.setCantidad(cantidad);

        Pedido p = new Pedido();
        p.setId(1L);
        p.setCliente("Cliente Web");
        p.setEstado(estado);
        p.setItems(new ArrayList<>(List.of(item)));
        return p;
    }
}
