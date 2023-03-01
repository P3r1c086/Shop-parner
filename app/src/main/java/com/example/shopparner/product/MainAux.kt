package com.example.shopparner.product

import com.example.shopparner.entities.Product

/**
 * Proyect: Shop Parner
 * From: com.example.shopparner
 * Create by Pedro Aguilar Fernández on 01/12/2022 at 13:19
 * More info: linkedin.com/in/pedro-aguilar-fernández-167753140
 * All rights reserved 2022
 **/

/**
 * Interfaz necesaria para dar de alta o actualizar un producto. Por eso el producto puede ser null.
 * Cuando demos de alta un nuevo producto sera null y cuando se trate de actualizarlo sera != de null
 */
interface MainAux {
    fun getProductSelected(): Product?
}