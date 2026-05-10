package com.smartlogix.pedidos.model;

import lombok.Data;

@Data
public class ProductoDTO {
    private Long id;
    private String sku;
    private String nombre;
    private String descripcion;
    private Integer stock;
    private Double precio;
}