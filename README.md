# SmartLogix Pedidos (Microservicio de Pedidos)

## Descripción
El **Microservicio de Pedidos** gestiona todas las operaciones relacionadas con los pedidos de compra en SmartLogix. Es responsable de:

- Crear y validar pedidos
- Gestionar el estado de los pedidos (APROBADO, RECHAZADO, etc.)
- Controlar el stock en coordinación con el MS-Inventario
- Permitir edición de pedidos existentes
- Calcular totales y gestionar ítems del pedido

## Tecnologías Utilizadas
- **Java 21**
- **Spring Boot 4.0.6**
- **Spring Data JPA** para persistencia
- **MySQL** como base de datos
- **Resilience4j** para circuit breaker
- **Maven** como gestor de dependencias

## Cómo Ejecutar

### Prerrequisitos
- Java 21 instalado
- Maven instalado
- MySQL ejecutándose en localhost:3306
- MS-Inventario ejecutándose (para validación de stock)

### Pasos para Ejecutar
1. Navega al directorio del proyecto:
   ```bash
   cd smartlogix-pedidos/pedidos
   ```

2. Ejecuta la aplicación:
   ```bash
   ./mvnw spring-boot:run
   ```

3. La aplicación se ejecutará en: `http://localhost:8086`

### Configuración de Base de Datos
- **URL**: `jdbc:mysql://localhost:3306/db_pedidos?createDatabaseIfNotExist=true`
- **Usuario**: `root`
- **Contraseña**: (vacía)
- **DDL Auto**: `update` (crea tablas automáticamente)

## Endpoints Principales
- `GET /api/pedidos` - Lista todos los pedidos
- `GET /api/pedidos/{id}` - Obtener pedido por ID
- `POST /api/pedidos` - Crear nuevo pedido (valida stock)
- `PUT /api/pedidos/{id}` - Actualizar pedido existente
- `DELETE /api/pedidos/{id}` - Eliminar pedido

## Estados de Pedido
- `APROBADO`: Pedido válido con stock suficiente
- `RECHAZADO_FALTA_STOCK`: No hay suficiente stock
- `RECHAZADO_FALTA_STOCK_EN_EDICION`: Stock insuficiente al editar
- `PENDIENTE_POR_FALLA_SISTEMA`: Error de comunicación con MS-Inventario

## Modelo de Datos
### Pedido
- `id`: Long (autogenerado)
- `estado`: String
- `cliente`: String
- `items`: List<ItemPedido>

### ItemPedido
- `id`: Long (autogenerado)
- `skuProducto`: String
- `cantidad`: Integer

## Arquitectura
Implementa el patrón Circuit Breaker para resiliencia en la comunicación con MS-Inventario. Utiliza transacciones JPA para asegurar consistencia en operaciones que afectan múltiples entidades.
