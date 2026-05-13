package com.smartlogix.pedidos.controller;

import com.smartlogix.pedidos.model.ItemPedido;
import com.smartlogix.pedidos.model.Pedido;
import com.smartlogix.pedidos.service.PedidoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoControllerTest {

    @Mock
    private PedidoService pedidoService;

    @InjectMocks
    private PedidoController pedidoController;

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
    @DisplayName("listarPedidos() retorna 200 OK con la lista de pedidos")
    void listarPedidos_retornaLista() {
        when(pedidoService.obtenerTodos()).thenReturn(List.of(pedido));

        ResponseEntity<List<Pedido>> respuesta = pedidoController.listarPedidos();

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).hasSize(1);
        assertThat(respuesta.getBody().get(0).getCliente()).isEqualTo("Cliente Web");
        verify(pedidoService).obtenerTodos();
    }

    @Test
    @DisplayName("listarPedidos() retorna 200 OK con lista vacía cuando no hay pedidos")
    void listarPedidos_listaVacia_retorna200() {
        when(pedidoService.obtenerTodos()).thenReturn(List.of());

        ResponseEntity<List<Pedido>> respuesta = pedidoController.listarPedidos();

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isEmpty();
    }

    @Test
    @DisplayName("registrarPedido() retorna 201 CREATED con el pedido creado")
    void registrarPedido_retorna201() {
        when(pedidoService.crearPedido(any(Pedido.class))).thenReturn(pedido);

        ResponseEntity<Pedido> respuesta = pedidoController.registrarPedido(pedido);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().getEstado()).isEqualTo("APROBADO");
        verify(pedidoService).crearPedido(pedido);
    }

    @Test
    @DisplayName("obtenerPedido() retorna 200 OK si el pedido existe")
    void obtenerPedido_existente_retorna200() {
        when(pedidoService.obtenerPedidoPorId(1L)).thenReturn(Optional.of(pedido));

        ResponseEntity<Pedido> respuesta = pedidoController.obtenerPedido(1L);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().getId()).isEqualTo(1L);
        verify(pedidoService).obtenerPedidoPorId(1L);
    }

    @Test
    @DisplayName("obtenerPedido() retorna 404 NOT FOUND si el pedido no existe")
    void obtenerPedido_noExistente_retorna404() {
        when(pedidoService.obtenerPedidoPorId(99L)).thenReturn(Optional.empty());

        ResponseEntity<Pedido> respuesta = pedidoController.obtenerPedido(99L);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuesta.getBody()).isNull();
    }

    @Test
    @DisplayName("actualizarPedido() retorna 200 OK si el pedido existe")
    void actualizarPedido_existente_retorna200() {
        when(pedidoService.actualizarPedido(1L, pedido)).thenReturn(pedido);

        ResponseEntity<Pedido> respuesta = pedidoController.actualizarPedido(1L, pedido);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().getId()).isEqualTo(1L);
        verify(pedidoService).actualizarPedido(1L, pedido);
    }

    @Test
    @DisplayName("actualizarPedido() retorna 404 NOT FOUND si el pedido no existe")
    void actualizarPedido_noExistente_retorna404() {
        when(pedidoService.actualizarPedido(99L, pedido))
                .thenThrow(new RuntimeException("No se encontró el pedido con ID: 99"));

        ResponseEntity<Pedido> respuesta = pedidoController.actualizarPedido(99L, pedido);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("eliminarPedido() retorna 204 NO CONTENT si el pedido existe")
    void eliminarPedido_existente_retorna204() {
        doNothing().when(pedidoService).eliminarPedido(1L);

        ResponseEntity<Void> respuesta = pedidoController.eliminarPedido(1L);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(pedidoService).eliminarPedido(1L);
    }

    @Test
    @DisplayName("eliminarPedido() retorna 404 NOT FOUND si el pedido no existe")
    void eliminarPedido_noExistente_retorna404() {
        doThrow(new RuntimeException("Pedido no encontrado con el ID: 99"))
                .when(pedidoService).eliminarPedido(99L);

        ResponseEntity<Void> respuesta = pedidoController.eliminarPedido(99L);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
