package com.example.shopparner.fcm

import android.content.Context
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley

/**
 * Proyect: Shop Parner
 * From: com.example.shopparner.fcm
 * Create by Pedro Aguilar Fernández on 04/01/2023 at 11:17
 * More info: linkedin.com/in/pedro-aguilar-fernández-167753140
 * All rights reserved 2023
 **/
class VolleyHelper(contex: Context) {

    //hacer patron Sigleton
    companion object{
        @Volatile
        private var INSTANCE: VolleyHelper? = null
        fun getInstance(context: Context) = INSTANCE ?: synchronized(this){
            INSTANCE ?: VolleyHelper(context).also { INSTANCE = it }
        }
    }

    private val requestQueue: RequestQueue by lazy {
        Volley.newRequestQueue(contex.applicationContext)
    }

    /**
     * Funcion para agregar las solicitudes a nuestra cola
     * <T> significa de tipo generico
     */
    fun <T> addToRequestQueue(req: Request<T>){
        requestQueue.add(req)
    }
}