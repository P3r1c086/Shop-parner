package com.example.shopparner.product

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.GridLayoutManager
import com.example.shopparner.add.AddDialogFragment
import com.example.shopparner.Constants
import com.example.shopparner.entities.Product
import com.example.shopparner.R
import com.example.shopparner.databinding.ActivityMainBinding
import com.example.shopparner.order.OrderActivity
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.ErrorCodes
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity(), OnProductListener, MainAux {

    private lateinit var binding : ActivityMainBinding

    //Estas dos variables globales sirven para detectar el estado actual de una sesion, con el fin
    // de retener una sesioon activa
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var authStateListener: FirebaseAuth.AuthStateListener

    private lateinit var adapter: ProductAdapter

    //variable para listar en tiempo real
    private lateinit var firestoreListener: ListenerRegistration

    private var productSelected: Product? = null

    private lateinit var firebaseAnalytics: FirebaseAnalytics

    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        //dentro procesamos la respuesta
        val response = IdpResponse.fromResultIntent(it.data)
        if (it.resultCode == RESULT_OK){
            //corroboramos que exista un usuario autenticado
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null){
                Toast.makeText(this, "Bienvenido", Toast.LENGTH_SHORT).show()

                firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN){
                    param(FirebaseAnalytics.Param.SUCCESS, 100)//100 = login successfully
                    param(FirebaseAnalytics.Param.METHOD, "login")
                }
            }
        }else{
            //para poder salirnos de la app y que no vuelva a lanzar esa pantalla de inicio de sesion
            if (response == null){//significa que el usuario ha pulsado hacia atras
                Toast.makeText(this, "Hasta pronto", Toast.LENGTH_SHORT).show()


                firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN){
                    param(FirebaseAnalytics.Param.SUCCESS, 200)//200 = login cancel
                    param(FirebaseAnalytics.Param.METHOD, "login")
                }
                //finalizamos la actividad
                finish()
            }else{//si se ha producido algun tipo de error distinto de que el usuario retroceda porque quiere
                //let quiere decir que si no es null haga lo que le indicamos entre llaves
                response.error?.let {
                    if (it.errorCode == ErrorCodes.NO_NETWORK){//si pulsas en NO_NETWORK se ven todos los tipos de errores que puedes abordar
                        Toast.makeText(this, "Sin red.", Toast.LENGTH_SHORT).show()
                    }else{//si el error no esta contemplado
                        Toast.makeText(this, "Código de error: ${it.errorCode}",
                            Toast.LENGTH_SHORT).show()
                    }

                    firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN){
                        param(FirebaseAnalytics.Param.SUCCESS, it.errorCode.toLong())
                        param(FirebaseAnalytics.Param.METHOD, "login")
                    }
                }
            }

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configAuth()
        configRecyclerView()
//        configFirestore() lo comento porque este metodo carga el listado, pero es una consulta que
//        solo se ejecuta una vez, y ahora queremos que detecte los cambios en tiempo real
        //configFirestoreRealtime()  este lo comento aqui porque se ejecuta en el metodo onResume()
        configButtons()
        configAnalytics()
    }

    private fun configAuth(){
        //instanciamos las variables globales
        firebaseAuth = FirebaseAuth.getInstance()
        authStateListener = FirebaseAuth.AuthStateListener { auth ->
            //si el usuario que entra esta autenticado
            if (auth.currentUser != null){
                //ponemos el nombre del usuario en la barra de accion
                supportActionBar?.title = auth.currentUser?.displayName
                //por default el contenido estara oculto y si el usuario se autentica correctamente
                //podra ver el contenido de la app, en este caso tvInit, pero se puede cambiar por un
                // contenedor , un formulario, etc, es decir el padre que contenga el contenido.
                binding.nsvProducts.visibility = View.VISIBLE
                binding.llProgress.visibility = View.GONE
                binding.efab.show()
            }else{ //si no lo esta
                //crear variable con todos los proveedores de autenticado
                val providers = arrayListOf(
                    AuthUI.IdpConfig.EmailBuilder().build(),
                    AuthUI.IdpConfig.GoogleBuilder().build())

                resultLauncher.launch(AuthUI.getInstance()
                    .createSignInIntentBuilder()
                    .setAvailableProviders(providers)
                    .setIsSmartLockEnabled(false)//para que no aparezca el dialog con las opciones de los usuarios que ya han logueado antes
                    .build())
            }
        }
    }

    //VINCULAR LAS DOS VARIABLES GLOBALES EN LOS METODOS DEL CICLO DE VIDA onResume() Y onPause()
    override fun onResume() {
        super.onResume()
        firebaseAuth.addAuthStateListener(authStateListener)
        // Lo agregaremos cada vez que se reanude la app.
        configFirestoreRealtime()
    }

    override fun onPause() {
        super.onPause()
        //sera para remover
        firebaseAuth.removeAuthStateListener(authStateListener)
        //quitamos el listener cada vez que se pause la app para que no se quede constantemente
        // escuchando y la app consuma menos recursos.
        firestoreListener.remove()
    }

    private fun configRecyclerView(){
        //inicializar el adaptador
        adapter = ProductAdapter(mutableListOf(), this)
        binding.recyclerView.apply {
            // 2 es el num de columnas, HORIZONTAL es la orientacion
            layoutManager = GridLayoutManager(this@MainActivity, 2,
                GridLayoutManager.VERTICAL, false)
            adapter = this@MainActivity.adapter
        }
//        (1..20).forEach{
//            val product = Product(it.toString(), "Producto: $it","Este producto es el $it",
//                "", it, it + 1.1)
//            adapter.add(product)
//        }
    }

    //inflar el munu
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    //programarle las acciones al menu
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.action_sign_out ->{
                AuthUI.getInstance().signOut(this)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Sesión terminada.", Toast.LENGTH_SHORT).show()

                        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN){
                            param(FirebaseAnalytics.Param.SUCCESS, 100)//100 = sign out successfully
                            param(FirebaseAnalytics.Param.METHOD, "sign_out")
                        }
                    }
                    .addOnCompleteListener {
                        if (it.isSuccessful){//si la tarea fue exitosa, en este caso es cerrar sesion
                            //haremos invisible el contenido
                            binding.nsvProducts.visibility = View.GONE
                            binding.llProgress.visibility = View.VISIBLE
                            binding.efab.hide()
                        }else{
                            Toast.makeText(this, "No se puedo cerrar la sesión.", Toast.LENGTH_SHORT).show()

                            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN){
                                param(FirebaseAnalytics.Param.SUCCESS, 201)//201 = error sign out
                                param(FirebaseAnalytics.Param.METHOD, "sign_out")
                            }
                        }
                    }
            }
            R.id.action_order_history -> startActivity(Intent(this, OrderActivity::class.java))
        }
        return super.onOptionsItemSelected(item)
    }

    private fun configFirestore(){
        //hacer una isntancia a la bd
        val db = FirebaseFirestore.getInstance()

        db.collection(Constants.COLL_PRODUCTS)
            .get()
            .addOnSuccessListener { snapshots ->
                for (document in snapshots){
                    //extraer cada documento y convertirlo a producto
                    val product = document.toObject(Product::class.java)
                    //asignamos como id el id aleatorio que crea la bd
                    product.id = document.id
                    adapter.add(product)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al consultar datos.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun configFirestoreRealtime(){
        //instanciamos la bd
        val db = FirebaseFirestore.getInstance()
        //creamos una referencia a la coleccion donde estan los productos
        val productRef = db.collection(Constants.COLL_PRODUCTS)
        
        //para capturar los cambios
        firestoreListener = productRef.addSnapshotListener { snapshots, error ->
            if (error != null){
                Toast.makeText(this, "Error al consultar datos.", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }
            //en caso de que no exista error
            for (snapshot in snapshots!!.documentChanges){

                //extraer cada documento y convertirlo a producto
                val product = snapshot.document.toObject(Product::class.java)
                //asignamos como id el id aleatorio que crea la bd
                product.id = snapshot.document.id
                //sentencia when para detectar el tipo de evento
                when(snapshot.type){
                    DocumentChange.Type.ADDED -> adapter.add(product)
                    DocumentChange.Type.MODIFIED -> adapter.update(product)
                    DocumentChange.Type.REMOVED -> adapter.delete(product)
                }
            }
        }
    }

    private fun configButtons(){
        binding.efab.setOnClickListener {
            //debemos darle un valor al productSelected antes de que se inicialice nuestro fragment
            productSelected = null
            //hacemos una instancia de un objeto de la clase AddDialogFragment
            AddDialogFragment().show(supportFragmentManager, AddDialogFragment::class.java.simpleName)
        }
    }

    private fun configAnalytics(){
        firebaseAnalytics = Firebase.analytics
    }


    /**
     * En este metodo sera la adiccion
     */
    override fun onClick(product: Product) {
        //debemos darle un valor al productSelected antes de que se inicialice nuestro fragment,
        //en este caso sera el producto selecionado
        productSelected = product
        //hacemos una instancia de un objeto de la clase AddDialogFragment
        AddDialogFragment().show(supportFragmentManager, AddDialogFragment::class.java.simpleName)
    }

    override fun onLongClick(product: Product) {
        //hacemos una instancia a la bd
        val db = FirebaseFirestore.getInstance()
        //creamos una referencia a la coleccion donde estan todos los productos
        val productRef = db.collection(Constants.COLL_PRODUCTS)
        //ahora hay que puntualizar cual de ellos queremos eliminar
        product.id?.let { id ->
            productRef.document(id)
            //ya estamos posicionado en el documento en especifico
                .delete()
                .addOnFailureListener {
                    Toast.makeText(this, "Error al eliminar.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    //va a devolver la variable global productSelected
    override fun getProductSelected(): Product? = productSelected
}