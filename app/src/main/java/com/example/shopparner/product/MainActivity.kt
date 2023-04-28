package com.example.shopparner.product

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.GridLayoutManager
import com.example.shopparner.add.AddDialogFragment
import com.example.shopparner.Constants
import com.example.shopparner.entities.Product
import com.example.shopparner.R
import com.example.shopparner.databinding.ActivityMainBinding
import com.example.shopparner.order.OrderActivity
import com.example.shopparner.promo.PromoFragment
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.ErrorCodes
import com.firebase.ui.auth.IdpResponse
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage

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

    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
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

    //cremos una variable global que nos lleve un contador general con el cual podamos mostrarle al
    // usuario que se ha subido por ej 1 de 5 imagenes. Y una variable global con una lista de Uris
    private var count = 0
    private val uriList = mutableListOf<Uri>()
    private val progressSnackbar: Snackbar by lazy {
        //LENGTH_INDEFINITE significa que se mostrara hasta que le digamos manualmente lo contrario
        Snackbar.make(binding.root, "", Snackbar.LENGTH_INDEFINITE)
    }

    private var galleryResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        if (it.resultCode == RESULT_OK){
            if (it.data?.clipData != null){
                //aqui ya podemos empezar a procesar cuantas fotografias han sido seleccionadas
                count = it.data!!.clipData!!.itemCount

                //con esto recolectamos las uris
                for (i in 0..count-1){
                    uriList.add(it.data!!.clipData!!.getItemAt(i).uri)
                }
                if (count > 0) uploadImage(0)//comenzamos a subir la imagen
            }
        }
    }

    private fun uploadImage(position: Int) {
        //conseguimos el usuario autenticado
        FirebaseAuth.getInstance().currentUser?.let { user ->
            progressSnackbar.apply {
                setText("Subiendo imagen ${position + 1} de $count...")
                show()
            }
            //hacemos una instancia a la raiz del servidor
            //creamos una nueva referencia que apunta al "image${position+1}" de la foto
            val productRef = FirebaseStorage.getInstance().reference
                .child(user.uid)
                .child(Constants.PATH_PRODUCT_IMAGES)
                .child(productSelected!!.id!!)
                .child("image${position+1}")
            //comenzamos a subir la imagen. uri es photoSelectedUri
            productRef.putFile(uriList[position])
                .addOnSuccessListener {
                //implementamos una logica que nos pueda llevar a cabo un recorrido global por
                // aquellas imagenes que se van subiendo a nuestro servidor
                    if (position < count-1){
                        uploadImage(position + 1)
                    }else{ //cuando se llege al limite
                        progressSnackbar.apply {
                            setText("Imágenes subidas correctamente!")
                            setDuration(Snackbar.LENGTH_SHORT)
                            show()
                        }
                    }
                }
                .addOnFailureListener{
                    progressSnackbar.apply {
                        setText("Error al subir la imagen ${position + 1}")
                        setDuration(Snackbar.LENGTH_LONG)
                        show()
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

                authLauncher.launch(AuthUI.getInstance()
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
            R.id.action_promo -> {
                PromoFragment().show(supportFragmentManager, PromoFragment::class.java.simpleName)
            }
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

    /**
     * Metodo para lanzar un dialog y elgeir entre eliminar o agregar mas fotos
     */
    override fun onLongClick(product: Product) {
        val adapter = ArrayAdapter<String>(this, android.R.layout.select_dialog_singlechoice)
        adapter.add("Eliminar")
        adapter.add("Añadir fotos")

        MaterialAlertDialogBuilder(this)
            .setAdapter(adapter){ dialogInterface: DialogInterface, position: Int ->
                when(position){
                    0 -> confirmDeleteProduct(product)
                    1 -> { //lanzamos un intent para poder seleccionar la galeria
                        productSelected = product
                        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        //configuramos la seleccion multiple
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        galleryResult.launch(intent)
                    }
                }
            }
            .show()
    }

    /**
     * Metodo para borrar un producto
     */
    private fun confirmDeleteProduct(product: Product){
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.product_dialog_delete_title)
            .setMessage(R.string.product_dialog_delete_msg)
            .setPositiveButton(R.string.product_dialog_delete_confirm){_,_ ->
                //hacemos una instancia a la bd
                val db = FirebaseFirestore.getInstance()
                //creamos una referencia a la coleccion donde estan todos los productos
                val productRef = db.collection(Constants.COLL_PRODUCTS)
                //ahora hay que puntualizar cual de ellos queremos eliminar
                product.id?.let { id ->
                    product.imgUrl?.let { url ->
                        //extraemos la referencia en base a la url
                        //hacemos referencia a fireStorage, en concreto, al id del producto para borrarlo
                        //ponemos como hijo una carpeta donde almacenar las imagenes
                        val photRef = FirebaseStorage.getInstance().getReferenceFromUrl(url)
//                        FirebaseStorage.getInstance().reference.child(Constants.PATH_PRODUCT_IMAGES).child(id)
                        photRef
                            .delete()
                            .addOnSuccessListener {
                                productRef.document(id)
                                    //ya estamos posicionado en el documento en especifico
                                    .delete()
                                    .addOnFailureListener {
                                        Toast.makeText(this, "Error al eliminar registro.", Toast.LENGTH_SHORT).show()
                                    }
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Error al eliminar foto.", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    //va a devolver la variable global productSelected
    override fun getProductSelected(): Product? = productSelected
}