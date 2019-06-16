package latte.example.com.customnestedscrollapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val rv = findViewById<ReRecyclerView>(R.id.rv).apply{
            layoutManager = LinearLayoutManager(context,RecyclerView.VERTICAL,false)
            adapter = Adapter().apply {
                val mutableList = mutableListOf<String>()
                for(i in 1..20){
                    mutableList.add("Content$i")
                }
                setItemList(mutableList)
            }

        }


    }
}

class Adapter : RecyclerView.Adapter<Viewholder>(){
    var list = listOf<String>()
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Viewholder{
        return Viewholder(LayoutInflater.from(parent.context).inflate(R.layout.item,parent,false))
    }

    override fun getItemCount()     = list.size

    override fun onBindViewHolder(holder: Viewholder, position: Int) {
        holder.onBind(list[position])
    }

    fun setItemList(list : List<String>){
      this.list = list
    notifyDataSetChanged()
    }

}

class Viewholder(view :View) :RecyclerView.ViewHolder(view){
   val title = view.findViewById<TextView>(R.id.title)
    fun onBind(str : String){
        title.text = str
    }
}