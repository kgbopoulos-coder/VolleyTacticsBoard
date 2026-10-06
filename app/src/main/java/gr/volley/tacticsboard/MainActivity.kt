package gr.volley.tacticsboard
import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
class MainActivity:Activity(),LocalBoardSync.Listener{
 private lateinit var board:TacticsBoardView; private lateinit var sync:LocalBoardSync
 override fun onCreate(b:Bundle?){super.onCreate(b);window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);board=TacticsBoardView(this);setContentView(board);sync=LocalBoardSync(this,this);board.onSyncMessage={sync.send(it)};sync.start()}
 override fun onPeerState(c:Boolean)=runOnUiThread{board.setConnected(c)}
 override fun onMessage(m:String)=runOnUiThread{board.applyRemoteMessage(m)}
 override fun onDestroy(){sync.stop();super.onDestroy()}
}
