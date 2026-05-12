package com.smartlogix.pedidos;

import com.smartlogix.pedidos.model.ItemPedido;
import com.smartlogix.pedidos.model.Pedido;
import com.smartlogix.pedidos.repository.PedidoRepository;
import com.smartlogix.pedidos.service.PedidoService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private PedidoService pedidoService;

    private Pedido pedido;

    @BeforeEach
    void setUp() {
        ItemPedido item = new ItemPedido();
        item.setSkuProducto("SKU-001");
        item.setCantidad(2);

        pedido = new Pedido();
        pedido.setId(1L);
        pedido.setCliente("Cliente Web");
        pedido.setEstado("APROBADO");
        pedido.setItems(new ArrayList<>(List.of(item)));
    }

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

    @Test
    @DisplayName("obtenerPedidoPorId() retorna el pedido si existe")
    void obtenerPedidoPorId_existente_retornaPedido() {

        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));

        var resultado = pedidoService.obtenerPedidoPorId(1L);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("fallbackCrearPedido() asigna estado PENDIENTE_POR_FALLA_SISTEMA")
    void fallback_asignaEstadoPendiente() {
        // simulamos que el repositorio guarda el pedido en modo fallback
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        // invocamos el método fallback directamente (patrón Circuit Breaker)
        var resultado = pedidoService.fallbackCrearPedido(
                pedido,
                new RuntimeException("MS-Inventario no disponible"));

        // degradación controlada: el pedido queda pendiente, no se pierde
        assertThat(resultado.getEstado()).isEqualTo("PENDIENTE_POR_FALLA_SISTEMA");
        verify(pedidoRepository).save(pedido);
    }

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
}
