package com.example.shopparner

import android.app.Application
import com.example.shopparner.fcm.VolleyHelper

/**
 * Proyect: Shop Parner
 * From: com.example.shopparner
 * Create by Pedro Aguilar Fernández on 04/01/2023 at 11:31
 * More info: linkedin.com/in/pedro-aguilar-fernández-167753140
 * All rights reserved 2023
 **/
class ShopParnerApplication : Application() {

    companion object{
        lateinit var volleyHelper: VolleyHelper
    }

    override fun onCreate() {
        super.onCreate()

        volleyHelper = VolleyHelper.getInstance(this)
    }
}