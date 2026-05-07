package com.smartlogix.pedidos.model;

import lombok.Data;

@Data
public class ProductoDTO {
    private String sku;
    private Integer stock;
    private Double precio;
}