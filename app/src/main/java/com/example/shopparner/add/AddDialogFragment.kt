package com.example.shopparner.add

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.shopparner.Constants
import com.example.shopparner.entities.EventPost
import com.example.shopparner.entities.Product
import com.example.shopparner.databinding.FragmentDialogAddBinding
import com.example.shopparner.product.MainAux
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

/**
 * Proyect: Shop Parner
 * From: com.example.shopparner
 * Create by Pedro Aguilar Fernández on 30/11/2022 at 18:26
 * More info: linkedin.com/in/pedro-aguilar-fernández-167753140
 * All rights reserved 2022
 **/
class AddDialogFragment : DialogFragment(), DialogInterface.OnShowListener {

    private var  binding: FragmentDialogAddBinding? = null

    //variables para cancelar y confirmar
    private var positiveButton: Button? = null
    private var negativeButton: Button? = null

    private var product: Product? = null

    //variables globales para cargar imagen en la imageView o subirlo a cloud Storage
    private var photoSelectedUri: Uri? = null
    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        if (it.resultCode == Activity.RESULT_OK){
            photoSelectedUri = it.data?.data

           // binding?.imgProductPreview?.setImageURI(photoSelectedUri)

            //Glide tb puede cargar una imagen que venga localmente
            binding?.let {
                //cargar imagen
                Glide.with(this)
                    .load(photoSelectedUri)
                    //este es para que almacene la imagen descargada, para que no tenga que estar
                    // consultando cada vez que inicie la app. Tiene la desventaja que hasta que no cambie
                    // la url, la imagen va a ser la misma sin importar que el servidor si cambie
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(it.imgProductPreview)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        //si la actividad es != de null
        activity?.let{ activity->
            //inflamos la vista en el binding para poder ver los elementos que contiene
            binding = FragmentDialogAddBinding.inflate(LayoutInflater.from(context))

            binding?.let {
                //configurar el dialog
                val builder = AlertDialog.Builder(activity)
                    .setTitle("Agregar producto")
                    .setPositiveButton("Agregar", null)
                    .setNegativeButton("Cancelar", null)
                    .setView(it.root)
                //crear el dialog
                val dialog = builder.create()
                dialog.setOnShowListener(this)//este this es del OnShowListener que hereda la clase

                return dialog
            }
        }
        return super.onCreateDialog(savedInstanceState)
    }

    override fun onShow(dialogInterface: DialogInterface?) {
        //metodo que se encarga de inicializar la variable product
        initProduct()
        configButtons()

        val dialog = dialog as? AlertDialog
        dialog?.let {
            positiveButton = it.getButton(Dialog.BUTTON_POSITIVE)
            negativeButton = it.getButton(Dialog.BUTTON_NEGATIVE)

            positiveButton?.setOnClickListener {
                //recolectar los datos que tenemos en el dialog para despues llamar a save()
                //creamos un nuevo producto. El id debe ser generado automaticamente. Aqui lo omitimos
                //y empezamos por la propiedad name
                //como binding puede ser null
                binding?.let {
                    enableUI(false)//esto hace que se bloquee el dialog
                    //subir imagen al storage. Recibe un callBack
//                    uploadImage(product?.id) { eventPost ->
                    uploadReducedImage(product?.id) { eventPost ->
                        //si la imagen fue subida correctamente
                        if (eventPost.isSuccess){
                            if (product == null){//si el producto es null, lo creamos
                                val product = Product(name = it.etName.text.toString().trim(),
                                    description = it.etDescription.text.toString().trim(),
                                    imgUrl = eventPost.photoUrl,
                                    quantity = it.etQuantity.text.toString().toInt(),
                                    price = it.etPrice.text.toString().toDouble())

                                save(product, eventPost.documentId!!)
                            }else{//si no es null, retomamos nuestro producto y le damos los nuevos valores,
                                //es decir, es una actualizacion
                                product?.apply {
                                    name = it.etName.text.toString().trim()
                                    description = it.etDescription.text.toString().trim()
                                    imgUrl = eventPost.photoUrl
                                    quantity = it.etQuantity.text.toString().toInt()
                                    price = it.etPrice.text.toString().toDouble()
                                    //una vez tenemos el producto con las nuevas propiedades, llamamos a un
                                    // metodo que lo actualice
                                    update(this)//debido a apply, en vez poner el producto, ponemos el
                                    //contexto
                                }
                            }
                        }
                    }
                }
            }
            negativeButton?.setOnClickListener {
                //hacer que desaparezca el cuadro de dialog
                dismiss()
            }
        }
    }

    private fun initProduct() {
        //inicializamos la variable. Hacemos un casteo de forma segura
        //si el resultado del casteo no es null llamamos a getProductSelected
        product = (activity as? MainAux)?.getProductSelected()
        //rellenamos el formulario en base al producto cuando este no sea null
        product?.let { product->
            binding?.let {
                it.etName.setText(product.name)
                it.etDescription.setText(product.description)
                it.etQuantity.setText(product.quantity.toString())
                it.etPrice.setText(product.price.toString())

                //cargar imagen
                Glide.with(this)
                    .load(product.imgUrl)
                    //este es para que almacene la imagen descargada, para que no tenga que estar
                    // consultando cada vez que inicie la app. Tiene la desventaja que hasta que no cambie
                    // la url, la imagen va a ser la misma sin importar que el servidor si cambie
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(it.imgProductPreview)


            }
        }
    }

    private fun configButtons(){
        binding?.let {
            it.ibProduct.setOnClickListener {
                openGallery()
            }
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        resultLauncher.launch(intent)
    }

    /**
     * metodo para subir imagenes al storage
     */
    private fun uploadImage(productId: String?, callback: (EventPost)->Unit){ //que retorna Unit sig que no retorna nada
        //creamos una nueva instancia de EventPost, la cual va a contener el documento
        val eventPost = EventPost()
        //El sigo de Elvis, hace que en caso de que sea null, agarre el id del nuevo documento, sino
        // que se quede con el id del producto actual.
        //extraemos el id del document. Estamos reservando un lugar para que la imagen que subamos
        // tenga como nombre este id. Posteriormente, una vez que termine el proceso de subir vamos a
        // regresar ese documento para que la imagen que vayamos a subir sea asignada con el nombre
        // de este id y posteriormente, despues de que se suba nuestra imagen, ahora si, vamos a
        // agarra el mismo document id para insertar un nuevo registro
        eventPost.documentId = productId ?: FirebaseFirestore.getInstance().collection(Constants.COLL_PRODUCTS)
            .document().id
        //hacemos una instancia a la raiz del servidor
        val storageRef = FirebaseStorage.getInstance().reference
        //ponemos como hijo una carpeta donde almacenar las imagenes
            .child(Constants.PATH_PRODUCT_IMAGES)
        //si photoSelectedUri es != de null y binding tb
        photoSelectedUri?.let { uri ->
            binding?.let { binding ->
                //hacemos visible el progressbar
                binding.progressBar.visibility = View.VISIBLE
                //creamos una nueva referencia que apunta al id de la foto
                val photoRef = storageRef.child(eventPost.documentId!!)
                //comenzamos a subir la imagen. uri es photoSelectedUri
                photoRef.putFile(uri)
                        //para la barra de progreso al subir la foto
                    .addOnProgressListener {
                        //con esto obtenemos los bytes tranferidos respecto al total
                        val progress = (100 * it.bytesTransferred / it.totalByteCount).toInt()
                        it.run {
                            binding.progressBar.progress = progress
                            binding.tvProgress.text = String.format("%s%%", progress)
                        }
                    }
                    .addOnSuccessListener {
                        //extraemos la url para descargar
                        it.storage.downloadUrl.addOnSuccessListener { downloadUrl ->
                            Log.i("URL", downloadUrl.toString())
                            //la imagen ya ha sido subida al storage con putFile, ahora vamos a insertarla en Firestore
                            eventPost.isSuccess = true
                            eventPost.photoUrl = downloadUrl.toString()
                            callback(eventPost)
                        }
                    }
                    .addOnFailureListener{
                        Toast.makeText(activity, "Error al subir imagen.", Toast.LENGTH_SHORT).show()
                        eventPost.isSuccess = false
                        //hacemos que el dialog vuelva a estar disponible
                        enableUI(true)
                        callback(eventPost)
                    }
            }
        }
    }
    /**
     * metodo para subir imagenes comprimidas al storage
     */
    private fun uploadReducedImage(productId: String?, callback: (EventPost)->Unit){ //que retorna Unit sig que no retorna nada
        //creamos una nueva instancia de EventPost, la cual va a contener el documento
        val eventPost = EventPost()
        //El sigo de Elvis, hace que en caso de que sea null, agarre el id del nuevo documento, sino
        // que se quede con el id del producto actual.
        //extraemos el id del document. Estamos reservando un lugar para que la imagen que subamos
        // tenga como nombre este id. Posteriormente, una vez que termine el proceso de subir vamos a
        // regresar ese documento para que la imagen que vayamos a subir sea asignada con el nombre
        // de este id y posteriormente, despues de que se suba nuestra imagen, ahora si, vamos a
        // agarra el mismo document id para insertar un nuevo registro
        eventPost.documentId = productId ?: FirebaseFirestore.getInstance()
            .collection(Constants.COLL_PRODUCTS).document().id

        //verificamos que el usuario autenticado sea valido
        FirebaseAuth.getInstance().currentUser?.let { user ->
            //hacemos referencia a la carpeta contenedora de cada usuario
            val imageRef = FirebaseStorage.getInstance().reference.child(user.uid)
                //ponemos como hijo una carpeta donde almacenar las imagenes
                .child(Constants.PATH_PRODUCT_IMAGES)
            //creamos una nueva referencia que apunta al id de la foto
            val photoRef = imageRef.child(eventPost.documentId!!)
            //si photoSelectedUri es != de null y binding tb
            photoSelectedUri?.let { uri ->
                binding?.let { binding ->
                    //validamos que el metodo de getBitmapFromUri no es null
                    getBitmapFromUri(uri)?.let { bitmap ->
                        //ahora subimos la imagen en formato bitmap
                        //hacemos visible el progressbar
                        binding.progressBar.visibility = View.VISIBLE

                        //subiremos la foto en vez de con un URL, con un BitMap
                        val baos = ByteArrayOutputStream()
                        //comprimimos el bitmap. JPEG es el formato con menos peso
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                        //comenzamos a subir la imagen. uri es photoSelectedUri
                        photoRef.putBytes(baos.toByteArray())
                            //para la barra de progreso al subir la foto
                            .addOnProgressListener {
                                //con esto obtenemos los bytes tranferidos respecto al total
                                val progress = (100 * it.bytesTransferred / it.totalByteCount).toInt()
                                it.run {
                                    binding.progressBar.progress = progress
                                    binding.tvProgress.text = String.format("%s%%", progress)
                                }
                            }
                            .addOnSuccessListener {
                                //extraemos la url para descargar
                                it.storage.downloadUrl.addOnSuccessListener { downloadUrl ->
                                    Log.i("URL", downloadUrl.toString())
                                    //la imagen ya ha sido subida al storage con putFile, ahora vamos a insertarla en Firestore
                                    eventPost.isSuccess = true
                                    eventPost.photoUrl = downloadUrl.toString()
                                    callback(eventPost)
                                }
                            }
                            .addOnFailureListener{
                                Toast.makeText(activity, "Error al subir imagen.", Toast.LENGTH_SHORT).show()
                                eventPost.isSuccess = false
                                //hacemos que el dialog vuelva a estar disponible
                                enableUI(true)
                                callback(eventPost)
                            }
                    }
                }
            }
        }
    }

    /**
     * Metodo para no saturar el proceso de construir un BitMap a partir de nuestra URI
     */
    private fun getBitmapFromUri(uri: Uri): Bitmap?{
        //verificamos si nuestra activity es diferente de null
        activity?.let {
            //construimos un bitmap. Para versiones mas modernas de android se hara asi
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
                val source = ImageDecoder.createSource(it.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            }else{
                //para versiones anteriores a android P se hara de esta forma
                MediaStore.Images.Media.getBitmap(it.contentResolver, uri)
            }
            return  bitmap
        }
        return null//retornamos null en caso de que la activity sea null
    }


    private fun save(product: Product, documentId: String){
        //creamos una instancia de la base de datos de Firestore
        val db = FirebaseFirestore.getInstance()
        db.collection(Constants.COLL_PRODUCTS)
            //le seteamos el id de forma manual
            .document(documentId)
            .set(product)
//            .add(product)
            .addOnSuccessListener {
                Toast.makeText(activity, "Producto añadido.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(activity, "Error al insertar.", Toast.LENGTH_SHORT).show()

            }
            .addOnCompleteListener {
                //una vez haya terminado el proceso de insercion, se habilitara
                // el dialog, independientemente de si el proceso fue exitoso o no
                enableUI(true)
                //Unicamente sera mostrado el progressbar cuando haya una subida en proceso, es decir,
                //cuando se termine se ocultara
                binding?.progressBar?.visibility = View.INVISIBLE
                //cerrar el dialog fragment
                dismiss()
            }
    }

    private fun update(product: Product){
        //creamos una instancia de la base de datos de Firestore
        val db = FirebaseFirestore.getInstance()
        //comprobar que el id del producto no es null
        product.id?.let { id->
            //aqui estamos posicionados en products
            db.collection(Constants.COLL_PRODUCTS)
                //aqui estamos posicionados en el id del producto
                .document(id)
                //con set insertamos el producto en la bd
                .set(product)
                .addOnSuccessListener {
                    Toast.makeText(activity, "Producto actualizado.", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(activity, "Error al actualizar.", Toast.LENGTH_SHORT).show()

                }
                .addOnCompleteListener {
                    //una vez haya terminado el proceso de actualizacion, se habilitara
                    // el dialog, independientemente de si el proceso fue exitoso o no
                    enableUI(true)
                    //Unicamente sera mostrado el progressbar cuando haya una subida en proceso, es decir,
                    //cuando se termine se ocultara
                    binding?.progressBar?.visibility = View.INVISIBLE
                    //cerrar el dialog fragment
                    dismiss()
                }
        }
    }

    /**
     * Metodo para habilitar o deshabilitar la pantalla de dialog y evitar errores.
     * Llamaremos a este metodo cada vez que intentemos hacer una accion dentro de Firestore
     */
    private fun enableUI(enable: Boolean){
        //botones
        positiveButton?.isEnabled = enable
        negativeButton?.isEnabled = enable
        //EditText
        binding?.let {
            //it es binding
            with(it){
                etName.isEnabled = enable
                etDescription.isEnabled = enable
                etQuantity.isEnabled = enable
                etPrice.isEnabled = enable
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        //para poder desvincular binding
        binding = null
    }
}