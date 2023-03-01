package com.example.shopparner.order

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.shopparner.R
import com.example.shopparner.databinding.ItemOrderBinding
import com.example.shopparner.entities.Order

/**
 * Proyect: Nilo
 * From: com.example.nilo.order
 * Create by Pedro Aguilar Fernández on 24/12/2022 at 18:33
 * More info: linkedin.com/in/pedro-aguilar-fernández-167753140
 * All rights reserved 2022
 **/
class OrderAdapter(private val orderList: MutableList<Order>, private val listener: OnOrderListener) :
    RecyclerView.Adapter<OrderAdapter.ViewHolder>() {

    private lateinit var context: Context

    private val aValues: Array<String> by lazy {
        context.resources.getStringArray(R.array.status_value)
    }
    private val aKeys: Array<Int> by lazy {
        context.resources.getIntArray(R.array.status_key).toTypedArray()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.item_order, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val order = orderList[position]

        holder.setListener(order)

        holder.binding.tvId.text = context.getString(R.string.order_id, order.id)

        //variable para poder concatenar todos los nombres
        var names = ""
        order.products.forEach {
            names += "${it.value.name}, "
        }
        //dropLast es para eliminar los 2 ultimos caracteres, para que no se quede la parte final ", "
        holder.binding.tvProductNames.text = names.dropLast(2)
        holder.binding.tvTotalPrice.text = context.getString(R.string.product_full_cart, order.totalPrice)
        val index = aKeys.indexOf(order.status)
        val statusAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, aValues)
        holder.binding.actvStatus.setAdapter(statusAdapter)
        if (index != -1){//si encontro el estado en el array
            holder.binding.actvStatus.setText(aValues[index], false)//false es para que no pueda ser editable y se comporte como un spinner
        }else{
            holder.binding.actvStatus.setText(context.getText(R.string.order_status_unknown), false)
        }

    }

    override fun getItemCount(): Int = orderList.size

    fun add(order: Order){
        orderList.add(order)
        //notificar que nuevo order ha sido insertado en la ultima posicion
        notifyItemInserted(orderList.size - 1)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view){
        val binding = ItemOrderBinding.bind(view)

        fun setListener(order: Order){
            binding.actvStatus.setOnItemClickListener { adapterView, view, position, id ->
                //asignamos la nueva posicion
                order.status = aKeys[position]
                listener.onStatusChange(order)
            }
            binding.chpChat.setOnClickListener {
                listener.onStarChat(order)
            }
        }
    }


}