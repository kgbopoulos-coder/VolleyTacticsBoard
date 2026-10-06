package gr.volley.tacticsboard

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class TacticsBoardView(context: Context) : View(context) {
    private data class Stroke(val points: List<PointF>, val erase: Boolean, val autoX: Boolean = false)
    private val strokes = mutableListOf<Stroke>()
    private val current = mutableListOf<PointF>()
    private var pendingXIndex: Int? = null
    private var eraserMode = false
    private var connected = false
    var onSyncMessage: ((String) -> Unit)? = null

    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(25,25,25);style=Paint.Style.STROKE;strokeWidth=dp(5f);strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND }
    private val erasePaint = Paint(drawPaint).apply { strokeWidth=dp(34f);xfermode=PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val courtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;style=Paint.Style.STROKE;strokeWidth=dp(3f) }
    private val buttonPaint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign=Paint.Align.CENTER;typeface=Typeface.DEFAULT_BOLD }
    private val toolbarHeight get()=dp(92f)
    private val buttonGap get()=dp(8f)

    init { setLayerType(LAYER_TYPE_SOFTWARE,null); background=GradientDrawable().apply{setColor(Color.rgb(245,245,245))} }

    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas); drawCourt(canvas)
        val save=canvas.saveLayer(0f,0f,width.toFloat(),height-toolbarHeight,null)
        strokes.forEach{drawStroke(canvas,it)}
        if(current.size>1) drawStroke(canvas,Stroke(current.toList(),eraserMode))
        canvas.restoreToCount(save)
        drawToolbar(canvas); drawConnection(canvas)
    }
    private fun drawStroke(canvas:Canvas,s:Stroke){
        if(s.points.isEmpty())return
        val p=Path();p.moveTo(s.points[0].x,s.points[0].y);for(i in 1 until s.points.size)p.lineTo(s.points[i].x,s.points[i].y)
        canvas.drawPath(p,if(s.erase)erasePaint else drawPaint)
    }
    private fun drawCourt(canvas:Canvas){
        val top=dp(20f);val bottom=height-toolbarHeight-dp(14f);val h=bottom-top;val cw=min(width-dp(28f),h*0.67f);val left=(width-cw)/2f;val right=left+cw
        val rect=RectF(left,top,right,bottom);val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(224,111,46);style=Paint.Style.FILL}
        canvas.drawRoundRect(rect,dp(5f),dp(5f),fill);canvas.drawRect(rect,courtPaint)
        val mid=(top+bottom)/2f;canvas.drawLine(left,mid,right,mid,courtPaint);val a=h/6f
        canvas.drawLine(left,mid-a,right,mid-a,courtPaint);canvas.drawLine(left,mid+a,right,mid+a,courtPaint)
    }
    private fun drawConnection(canvas:Canvas){
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=if(connected)Color.rgb(30,150,70) else Color.rgb(190,45,45);textSize=dp(10f);typeface=Typeface.DEFAULT_BOLD}
        canvas.drawText(if(connected)"● ΣΥΝΔΕΔΕΜΕΝΟ" else "● ΧΩΡΙΣ ΣΥΝΔΕΣΗ",dp(10f),dp(14f),p)
    }
    private fun drawToolbar(canvas:Canvas){
        val top=height-toolbarHeight;canvas.drawRect(0f,top,width.toFloat(),height.toFloat(),Paint().apply{color=Color.rgb(244,244,244)})
        val icons=arrayOf("✏","▰","↶","✕");val labels=arrayOf("ΣΧΕΔΙΟ","ΣΦΟΥΓΓΑΡΙ","UNDO","ΚΑΘΑΡΙΣΜΟΣ")
        val bw=(width-buttonGap*5)/4;val bh=dp(66f);val y=top+(toolbarHeight-bh)/2f
        for(i in 0..3){val x=buttonGap+i*(bw+buttonGap);val r=RectF(x,y,x+bw,y+bh);val selected=(i==0&&!eraserMode)||(i==1&&eraserMode)
            buttonPaint.color=if(selected)Color.rgb(21,101,192) else Color.WHITE;canvas.drawRoundRect(r,dp(12f),dp(12f),buttonPaint)
            textPaint.color=if(selected)Color.WHITE else Color.rgb(35,35,35);textPaint.textSize=dp(22f);canvas.drawText(icons[i],r.centerX(),r.centerY()-dp(4f),textPaint)
            textPaint.textSize=dp(10f);canvas.drawText(labels[i],r.centerX(),r.centerY()+dp(18f),textPaint)}
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        val top=height-toolbarHeight
        if(e.action==MotionEvent.ACTION_DOWN&&e.y>=top){toolbarTap(e.x);return true}
        if(e.y>=top)return true
        when(e.action){
            MotionEvent.ACTION_DOWN->{current.clear();current.add(PointF(e.x,e.y));invalidate()}
            MotionEvent.ACTION_MOVE->{current.add(PointF(e.x,e.y));invalidate()}
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{
                current.add(PointF(e.x,e.y))
                val fresh=Stroke(current.toList(),eraserMode)
                if(!eraserMode && tryMakeSymmetricX(fresh)) {
                    current.clear();invalidate();return true
                }
                strokes.add(fresh);sendStroke(fresh)
                pendingXIndex=if(!eraserMode && isDiagonal(fresh)) strokes.lastIndex else null
                current.clear();invalidate()
            }
        };return true
    }
    private fun bounds(s:Stroke):RectF{
        var l=Float.MAX_VALUE;var t=Float.MAX_VALUE;var r=-Float.MAX_VALUE;var b=-Float.MAX_VALUE
        s.points.forEach{l=minOf(l,it.x);t=minOf(t,it.y);r=maxOf(r,it.x);b=maxOf(b,it.y)}
        return RectF(l,t,r,b)
    }
    private fun isDiagonal(s:Stroke):Boolean{
        if(s.erase||s.points.size<2)return false
        val q=bounds(s);if(q.width()<dp(12f)||q.height()<dp(12f))return false
        val ratio=q.width()/q.height();return ratio in 0.45f..2.2f
    }
    private fun tryMakeSymmetricX(second:Stroke):Boolean{
        val idx=pendingXIndex?:return false
        if(idx !in strokes.indices||!isDiagonal(second)){pendingXIndex=null;return false}
        val first=strokes[idx];val a=bounds(first);val b=bounds(second)
        val cx1=a.centerX();val cy1=a.centerY();val cx2=b.centerX();val cy2=b.centerY()
        val size=maxOf(a.width(),a.height(),b.width(),b.height())
        // Generous recognition for fast timeout drawing: centers only need to be in the same area.
        val near=kotlin.math.hypot((cx1-cx2).toDouble(),(cy1-cy2).toDouble())<maxOf(dp(45f),(size*0.85f).toDouble())
        val f0=first.points.first();val f1=first.points.last();val s0=second.points.first();val s1=second.points.last()
        val dx1=f1.x-f0.x;val dy1=f1.y-f0.y;val dx2=s1.x-s0.x;val dy2=s1.y-s0.y
        val opposite=(dx1*dy1)*(dx2*dy2)<0
        if(!near||!opposite){pendingXIndex=null;return false}
        val cx=(cx1+cx2)/2f;val cy=(cy1+cy2)/2f
        val avgSize=(maxOf(a.width(),a.height())+maxOf(b.width(),b.height()))/2f
        val half=maxOf(dp(14f),avgSize/2f)
        strokes.removeAt(idx)
        val xStroke=Stroke(listOf(PointF(cx-half,cy-half),PointF(cx+half,cy+half)),false,true)
        strokes.add(xStroke);pendingXIndex=null
        onSyncMessage?.invoke("U");sendStroke(xStroke)
        return true
    }
    private fun sendStroke(s:Stroke){
        val usable=(height-toolbarHeight).coerceAtLeast(1f);val msg=buildString{append(if(s.autoX)"X|" else "S|");append(if(s.erase)"1" else "0");s.points.forEach{append("|");append(it.x/width);append(",");append(it.y/usable)}}
        onSyncMessage?.invoke(msg)
    }
    fun applyRemoteMessage(m:String){
        val p=m.split("|");when(p.firstOrNull()){
            "S","X"->{val erase=p.getOrNull(1)=="1";val pts=mutableListOf<PointF>();val usable=(height-toolbarHeight).coerceAtLeast(1f)
                for(i in 2 until p.size){val xy=p[i].split(",");if(xy.size==2){val nx=xy[0].toFloatOrNull();val ny=xy[1].toFloatOrNull();if(nx!=null&&ny!=null)pts.add(PointF(nx*width,ny*usable))}}
                if(pts.isNotEmpty())strokes.add(Stroke(pts,erase,p.firstOrNull()=="X"))}
            "U"->if(strokes.isNotEmpty())strokes.removeAt(strokes.lastIndex)
            "C"->strokes.clear()
        };invalidate()
    }
    fun setConnected(v:Boolean){connected=v;invalidate()}
    private fun toolbarTap(x:Float){
        val bw=(width-buttonGap*5)/4;val i=((x-buttonGap)/(bw+buttonGap)).toInt().coerceIn(0,3)
        when(i){0->eraserMode=false;1->eraserMode=true;2->if(strokes.isNotEmpty()){strokes.removeAt(strokes.lastIndex);pendingXIndex=null;onSyncMessage?.invoke("U")};3->{strokes.clear();pendingXIndex=null;onSyncMessage?.invoke("C")}}
        invalidate()
    }
    private fun dp(v:Float)=v*resources.displayMetrics.density
}
