package top.sanqii.photo

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.scwang.wave.MultiWaveHeader
import com.zyao89.view.zloading.ZLoadingDialog
import com.zyao89.view.zloading.Z_TYPE
import id.zelory.compressor.Compressor
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.*
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private val pickupCode = 11
    private val captureCode = 12
    private val requestsUrl = "https://api.sanqii.top"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_first, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity?.let { activity ->
            activity.findViewById<FloatingActionButton>(R.id.fab)?.setOnClickListener {
                context?.let { context ->
                    MaterialDialog(context).show {
                        title(text = "导入方式")
                        message(text = "请选择图片的来源, 您将使用它来选取图片作为素材。")
                        positiveButton(text = "相机") {
                            activity.apply {
                                findViewById<FloatingActionButton>(R.id.fab)?.setOnClickListener {
                                    captureImg()
                                }
                                findViewById<TextView>(R.id.textview_first).setText(R.string.choose_picture)
                                findViewById<MultiWaveHeader>(R.id.waveHeader).colorAlpha = 0F
                            }
                        }
                        negativeButton(text = "相册") {
                            activity.apply {
                                findViewById<FloatingActionButton>(R.id.fab)?.setOnClickListener {
                                    pickImg()
                                }
                                findViewById<TextView>(R.id.textview_first).setText(R.string.choose_picture)
                                findViewById<MultiWaveHeader>(R.id.waveHeader).colorAlpha = 0F
                            }
                        }
                    }
                }
            }
        }
    }

    // 通过intent选择本地图片
    private fun pickImg() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, pickupCode)
    }

    private var captureImg: File? = null

    // 通过相机选择图片
    private fun captureImg() {
        context?.let { context ->
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            captureImg = createFile(context, "jpg")
            val imgUri = FileProvider.getUriForFile(context, "top.sanqii.provider", captureImg!!)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, imgUri)
            startActivityForResult(intent, captureCode)
        }
    }

    // 创建一个文件, 并在程序退出时自动删除
    private fun createFile(context: Context, type: String): File {
        val fileName = "IMG_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        val file = File(context.filesDir, "$fileName.$type")
        println(">>\n>>${file.path}    $fileName     $type")
        file.deleteOnExit()

        return file
    }

    // 图片数据流保存到本地, 可调用其uri
    private fun streamToFile(context: Context, iStream: InputStream, type: String): File {
        val fileType = type.subSequence(type.lastIndexOf("/") + 1, type.length) as String
        val file = createFile(context, fileType)
        val oStream = file.outputStream()
        iStream.copyTo(oStream)
        oStream.flush()
        iStream.close()
        iStream.close()

        return file
    }

    // intent的回调函数
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        context?.let { context ->
            val dialog = ZLoadingDialog(context)
            dialog.setLoadingBuilder(Z_TYPE.STAR_LOADING)
                .setLoadingColor(Color.WHITE)
                .setHintText("Loading")
                .setHintTextColor(Color.WHITE)
                .setDurationTime(0.5)
                .setDialogBackgroundColor(Color.parseColor("#CC111111"))
                .setCancelable(false)

            when (requestCode) {
                pickupCode -> {
                    data?.data?.let { uri ->
                        activity?.let { activity ->
                            val bitmap = activity.contentResolver.openInputStream(uri)
                                ?.let { stream ->
                                    val fType = context.contentResolver.getType(uri) ?: "jpg"
                                    streamToFile(context, stream, fType)
                                }
                            bitmap?.let { file ->
                                view?.findViewById<ImageView>(R.id.imageView)
                                    ?.setImageURI(file.toUri())
                                // 当图片显示后,再更改浮动按钮所绑定的事件
                                activity.findViewById<FloatingActionButton>(R.id.fab)
                                    .setOnClickListener {
                                        dialog.show()
                                        // 上传图片并用compressor库处理文件压缩(需要使用协程)
                                        // 压缩的目的是因为文件太大将导致图片上传或下载显示失败
                                        GlobalScope.launch {
                                            uploadImg(context, file, dialog)
                                        }
                                        activity.findViewById<FloatingActionButton>(R.id.fab)
                                            .setOnClickListener {
                                                pickImg()
                                            }
                                    }

                            }

                        }
                    }
                }
                captureCode -> {
                    // val bitmap = data?.extras?.get("data") as Bitmap
                    // view?.findViewById<ImageView>(R.id.imageView)
                    //     ?.setImageBitmap(bitmap)
                    // val bitmapFile = bitmapToFile(context, bitmap)
                    val bitmapFile = captureImg
                    view?.findViewById<ImageView>(R.id.imageView)
                        ?.setImageURI(bitmapFile!!.toUri())

                    // 当图片显示后,再更改浮动按钮所绑定的事件
                    activity?.let { activity ->
                        activity.findViewById<FloatingActionButton>(R.id.fab)
                            .setOnClickListener {
                                dialog.show()

                                // 上传图片并用compressor库处理文件压缩(需要使用协程)
                                // 压缩的目的是因为文件太大将导致图片上传或下载显示失败
                                GlobalScope.launch {
                                    if (bitmapFile != null) {
                                        uploadImg(context, bitmapFile, dialog)
                                    }
                                }
                                activity.findViewById<FloatingActionButton>(R.id.fab)
                                    .setOnClickListener {
                                        captureImg()
                                    }
                            }
                    }
                }
                else -> return
            }
        }

    }

    private suspend fun uploadImg(
        context: Context,
        img: File,
        loadingDialog: ZLoadingDialog
    ) {
        // 若图片大于一兆, 则使用第三方库压缩图片
        val compressImg = if (img.length() > 1024 * 1024) Compressor.compress(context, img) else img

        // 使用OkHttp上传图片
        val client = OkHttpClient()
        val body = MultipartBody.Builder()
            // 由于后台的api格式, 提交的是表单的形式
            .setType(MultipartBody.FORM)
            // 参数为: 请求key、文件名称、文件体
            .addFormDataPart(
                "file", compressImg.name,
                RequestBody.create(MediaType.parse("application/octet-stream"), compressImg)
            )
            .build()
        val request = Request.Builder()
            .url(requestsUrl)
            .post(body)
            .build()
        val call = client.newCall(request)
        call.enqueue(object : Callback {
            // UI的更新需要在UI线程中更新, runOnUiThread
            override fun onFailure(call: Call?, e: IOException?) {
                e?.printStackTrace()
                activity?.runOnUiThread {
                    Toast.makeText(context, "照片处理失败,请稍后重试", Toast.LENGTH_SHORT).show()
                    loadingDialog.dismiss()
                }
            }

            override fun onResponse(call: Call?, response: Response) {
                val stream = response.body()!!.byteStream()
                val fType = context.contentResolver.getType(img.toUri()) ?: "jpg"
                val bitmap = streamToFile(context, stream, fType)
                activity?.runOnUiThread {
                    view?.findViewById<ImageView>(R.id.imageView)?.setImageURI(bitmap.toUri())
                    Toast.makeText(context, "照片处理完成", Toast.LENGTH_SHORT).show()
                    loadingDialog.dismiss()
                }
            }
        })
    }
}