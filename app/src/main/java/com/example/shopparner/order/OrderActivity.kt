package com.example.shopparner.order

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.shopparner.Constants
import com.example.shopparner.R
import com.example.shopparner.chat.ChatFragment
import com.example.shopparner.databinding.ActivityOrderBinding
import com.example.shopparner.entities.Order
import com.example.shopparner.fcm.NotificationRS
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class OrderActivity : AppCompatActivity(), OnOrderListener, OrderAux {

    private lateinit var binding: ActivityOrderBinding

    private lateinit var adapter: OrderAdapter

    private lateinit var orderSelected: Order

    private lateinit var firebaseAnalytics: FirebaseAnalytics

    //con estas dos variables tendremos acceso a los arrays de valores y claves
    private val aValues: Array<String> by lazy {
        resources.getStringArray(R.array.status_value)
    }
    private val aKeys: Array<Int> by lazy {
        resources.getIntArray(R.array.status_key).toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)


        setupRecyclerView()
        setupFirestore()
        configAnalytics()
    }
    private fun setupRecyclerView() {
        adapter = OrderAdapter(mutableListOf(), this)

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@OrderActivity)
            adapter = this@OrderActivity.adapter
        }
    }

    private fun setupFirestore(){
        val db = FirebaseFirestore.getInstance()

        db.collection(Constants.COLL_REQUESTS)
            .get()
            .addOnSuccessListener {
                for (document in it){
                    val order = document.toObject(Order::class.java)
                    order.id = document.id
                    adapter.add(order)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al consultar los datos.", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    private fun configAnalytics(){
        firebaseAnalytics = Firebase.analytics
    }

    private fun notifyClient(order: Order){
        val db = FirebaseFirestore.getInstance()

        db.collection(Constants.COLL_USERS)
            .document(order.clientId)
            .collection(Constants.COLL_TOKENS)
            .get()
            .addOnSuccessListener {
                //creamos una variable donde concatenar y separa por comas todos los tokens del user
                var tokensStr = ""
                for (document in it){
                    val tokenMap = document.data
                    tokensStr += "${tokenMap.getValue(Constants.PROP_TOKEN)},"
                }
                //si al menos hay un token
                if (tokensStr.length > 0) {
                    //esto es para quitar la ultima coma del string
                    tokensStr = tokensStr.dropLast(1)


                    //variable para poder concatenar todos los nombres de los productos
                    var names = ""
                    order.products.forEach {
                        names += "${it.value.name}, "
                    }
                    //dropLast es para eliminar los 2 ultimos caracteres, para que no se quede la parte final ", "
                    names = names.dropLast(2)

                    //extraemos el valor del estado actual
                    val index = aKeys.indexOf(order.status)

                    val notificationRS = NotificationRS()
                    //"aValues[index]" indica el estado del pedido
                    notificationRS.sendNotification(
                        "Tu pedido ha sido ${aValues[index]}",
                        names, tokensStr
                    )
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al consultar los datos.", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    override fun onStarChat(order: Order) {
        orderSelected = order

        val fragment = ChatFragment()

        //lanzamos el fragmento
        supportFragmentManager
            .beginTransaction()
            .add(R.id.containerMain, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onStatusChange(order: Order) {
        val db = FirebaseFirestore.getInstance()
        db.collection(Constants.COLL_REQUESTS)
            .document(order.id)
            //actualizamos la propiedad status con un nuevo valor <order.status>
            .update(Constants.PROP_STATUS, order.status)
            .addOnSuccessListener {
                Toast.makeText(this, "Orden actualizada.", Toast.LENGTH_SHORT).show()
                //Al cambiar de estado podemos enviarle al cliente una notificacion de que el estado
                // de su pedido ha cambiado
                notifyClient(order)
                //Analytics
                firebaseAnalytics.logEvent(FirebaseAnalytics.Event.ADD_SHIPPING_INFO){
                    val product = mutableListOf<Bundle>()
                    order.products.forEach{
                        val bundle = Bundle()
                        bundle.putString("id_product", it.key)
                        product.add(bundle)
                    }
                    param(FirebaseAnalytics.Param.SHIPPING, product.toTypedArray())
                    param(FirebaseAnalytics.Param.PRICE, order.totalPrice)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al actualizar orden.", Toast.LENGTH_SHORT).show()
            }
    }

    override fun getOrderSelected(): Order = orderSelected
}