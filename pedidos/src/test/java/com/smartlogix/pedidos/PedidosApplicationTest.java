package com.smartlogix.pedidos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class PedidosApplicationTest {

    @Test
    @DisplayName("PedidosApplication está anotada con @SpringBootApplication")
    void tieneAnotacionSpringBootApplication() {
        assertThat(PedidosApplication.class.isAnnotationPresent(SpringBootApplication.class)).isTrue();
    }

    @Test
    @DisplayName("PedidosApplication se puede instanciar sin errores")
    void seInstanciaCorrectamente() {
        PedidosApplication app = new PedidosApplication();
        assertThat(app).isNotNull();
    }

    @Test
    @DisplayName("PedidosApplication tiene el método main público y estático")
    void tieneMetodoMainPublicoEstatico() throws NoSuchMethodException {
        Method main = PedidosApplication.class.getDeclaredMethod("main", String[].class);

        assertThat(main).isNotNull();
        assertThat(Modifier.isPublic(main.getModifiers())).isTrue();
        assertThat(Modifier.isStatic(main.getModifiers())).isTrue();
        assertThat(main.getReturnType()).isEqualTo(void.class);
    }

    @Test
    @DisplayName("PedidosApplication pertenece al paquete correcto")
    void perteneceAlPaqueteCorrecto() {
        assertThat(PedidosApplication.class.getPackageName())
                .isEqualTo("com.smartlogix.pedidos");
    }
}
