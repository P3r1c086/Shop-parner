package com.example.shopparner.promo

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
import com.example.shopparner.databinding.FragmentPromoBinding
import com.example.shopparner.fcm.NotificationRS
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
class PromoFragment : DialogFragment(), DialogInterface.OnShowListener {

    private var  binding: FragmentPromoBinding? = null

    //variables para cancelar y confirmar
    private var positiveButton: Button? = null
    private var negativeButton: Button? = null

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
            binding = FragmentPromoBinding.inflate(LayoutInflater.from(context))

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
                    uploadReducedImage()
                }
            }
            negativeButton?.setOnClickListener {
                //hacer que desaparezca el cuadro de dialog
                dismiss()
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
     * metodo para subir imagenes comprimidas al storage
     */
    private fun uploadReducedImage(){
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
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                        //hacemos referencia a la carpeta contenedora de cada usuario
                        val promoRef = FirebaseStorage.getInstance().reference.child("promos")
                            //ponemos como hijo una carpeta donde almacenar las imagenes
                            .child(binding.etTopic.text.toString().trim())
                        promoRef.putBytes(baos.toByteArray())
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
                                    val notificationRS = NotificationRS()
                                    notificationRS.sendNotificationByTopic(
                                        binding.etTitle.text.toString().trim(),
                                        binding.etDescription.text.toString().trim(),
                                        binding.etTopic.text.toString().trim(),
                                        downloadUrl.toString()
                                    ){
                                        if (it){
                                            Toast.makeText(activity, "Promoción enviada.", Toast.LENGTH_SHORT).show()
                                            //cerramos el dialog al enviar la promocion
                                            dismiss()
                                        }else{
                                            Toast.makeText(activity, "Error, intente más tarde.", Toast.LENGTH_SHORT).show()
                                        }
                                        //hacemos que el dialog vuelva a estar disponible
                                        enableUI(true)
                                    }
                                }
                            }
                            .addOnFailureListener{
                                Toast.makeText(activity, "Error al subir imagen.", Toast.LENGTH_SHORT).show()
                                //hacemos que el dialog vuelva a estar disponible
                                enableUI(true)
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
            return  getResizedImage(bitmap, 480)
        }
        return null//retornamos null en caso de que la activity sea null
    }

    /**
     * Metodo para redimensionar las imagenes
     */
    private fun getResizedImage(image: Bitmap, maxSize: Int): Bitmap{
        var width = image.width
        var height = image.height
        if (width <= maxSize && height <= maxSize) return image

        //si entra aqui es porque la imagen tiene una dimension mas grande que el tamaña max
        val bitmapRatio = width.toFloat() / height.toFloat()
        if (bitmapRatio > 1){
            width = maxSize
            //la altura sera de un tamaño proporcional
            height = (width / bitmapRatio).toInt()
        }else{
            height = maxSize
            //la anchura sera de un tamaño proporcional
            width = (height / bitmapRatio).toInt()
        }
        //procedemos a crear esa escala del bitmap
        return Bitmap.createScaledBitmap(image, width, height, true)
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
                etTitle.isEnabled = enable
                etDescription.isEnabled = enable
                etTopic.isEnabled = enable
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        //para poder desvincular binding
        binding = null
    }
}