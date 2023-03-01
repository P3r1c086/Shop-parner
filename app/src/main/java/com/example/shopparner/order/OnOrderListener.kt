package com.example.shopparner.order

import com.example.shopparner.entities.Order

/**
 * Proyect: Nilo
 * From: com.example.nilo.order
 * Create by Pedro Aguilar Fernández on 24/12/2022 at 18:31
 * More info: linkedin.com/in/pedro-aguilar-fernández-167753140
 * All rights reserved 2022
 **/
interface OnOrderListener {
    fun onStarChat(order: Order)
    fun onStatusChange(order: Order)
}