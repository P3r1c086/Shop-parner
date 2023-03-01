package com.example.shopparner.fcm

import android.util.Log
import com.android.volley.AuthFailureError
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.example.shopparner.Constants
import com.example.shopparner.ShopParnerApplication
import org.json.JSONException
import org.json.JSONObject

/**
 * Proyect: Shop Parner
 * From: com.example.shopparner.fcm
 * Create by Pedro Aguilar Fernández on 04/01/2023 at 11:36
 * More info: linkedin.com/in/pedro-aguilar-fernández-167753140
 * All rights reserved 2023
 **/
//RS significa Remote Service
class NotificationRS {

    fun sendNotification(title: String, message: String, tokens: String) {
        //construimos un objeto que pueda llevar los parametros
        val params = JSONObject()
        params.put(Constants.PARAM_METHOD, Constants.SEND_NOTIFICATION)
        params.put(Constants.PARAM_TITLE, title)
        params.put(Constants.PARAM_MESSAGE, message)
        params.put(Constants.PARAM_TOKENS, tokens)

        val jsonObjectRequest: JsonObjectRequest = object : JsonObjectRequest(Method.POST,
            Constants.SHOP_PARNER_RS, params, Response.Listener { response ->
                try {
                    val success = response.getInt(Constants.PARAM_SUCCESS)
                    Log.i("Volley success", success.toString())
                    Log.i("response", response.toString())
                }catch (e: JSONException){
                    e.printStackTrace()
                    Log.e("Volley exception", e.localizedMessage)
                }
            }, Response.ErrorListener { error ->  
                if (error.localizedMessage != null){
                    Log.e("Volley error", error.localizedMessage)
                }
            }) {
            @Throws(AuthFailureError::class)
            override fun getHeaders(): MutableMap<String, String> {
                val paramsHeaders = HashMap<String, String>()
                paramsHeaders["Content-Type"] = "application/json; charset=utf-8"
                return super.getHeaders()
            }
        }
        //ya hemos configurado a json. Ahora podemos enviar la peticion
        ShopParnerApplication.volleyHelper.addToRequestQueue(jsonObjectRequest)
    }
}