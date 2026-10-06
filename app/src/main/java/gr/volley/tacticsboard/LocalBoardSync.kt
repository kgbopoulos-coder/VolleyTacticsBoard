package gr.volley.tacticsboard

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo\nimport android.net.wifi.WifiManager
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class LocalBoardSync(context: Context, private val listener: Listener) {
    interface Listener { fun onPeerState(connected: Boolean); fun onMessage(message: String) }
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager\n    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager\n    private var multicastLock: WifiManager.MulticastLock? = null
    private val serviceType = "_volleyboard._tcp."
    private val serviceName = "VolleyTacticsBoard-" + java.util.UUID.randomUUID().toString().take(8)
    private val clients = CopyOnWriteArrayList<Socket>()
    private var server: ServerSocket? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var registration: NsdManager.RegistrationListener? = null

    fun start() {
        thread {
            try {
                server = ServerSocket(0); register(server!!.localPort)
                while (!server!!.isClosed) attach(server!!.accept())
            } catch (_: Exception) {}
        }
        discover()
    }
    private fun register(port: Int) {
        val info = NsdServiceInfo().apply { serviceName=this@LocalBoardSync.serviceName; serviceType=this@LocalBoardSync.serviceType; setPort(port) }
        registration=object:NsdManager.RegistrationListener{
            override fun onServiceRegistered(i:NsdServiceInfo){}; override fun onRegistrationFailed(i:NsdServiceInfo,e:Int){}
            override fun onServiceUnregistered(i:NsdServiceInfo){}; override fun onUnregistrationFailed(i:NsdServiceInfo,e:Int){}
        }
        nsd.registerService(info,NsdManager.PROTOCOL_DNS_SD,registration)
    }
    private fun discover() {
        discovery=object:NsdManager.DiscoveryListener{
            override fun onDiscoveryStarted(t:String){}; override fun onDiscoveryStopped(t:String){}
            override fun onStartDiscoveryFailed(t:String,e:Int){}; override fun onStopDiscoveryFailed(t:String,e:Int){}
            override fun onServiceLost(i:NsdServiceInfo){}
            override fun onServiceFound(i:NsdServiceInfo){
                if(i.serviceName==serviceName)return
                nsd.resolveService(i,object:NsdManager.ResolveListener{
                    override fun onResolveFailed(s:NsdServiceInfo,e:Int){}
                    override fun onServiceResolved(s:NsdServiceInfo){ thread { try { attach(Socket(s.host,s.port)) } catch(_:Exception){} } }
                })
            }
        }
        nsd.discoverServices(serviceType,NsdManager.PROTOCOL_DNS_SD,discovery)
    }
    private fun attach(s:Socket){
        if(clients.any{it.inetAddress==s.inetAddress && it.port==s.port}){try{s.close()}catch(_:Exception){};return}
        clients.add(s); listener.onPeerState(true)
        thread {
            try { BufferedReader(InputStreamReader(s.getInputStream())).use { r -> while(true) listener.onMessage(r.readLine()?:break) } }
            catch(_:Exception){} finally { clients.remove(s); try{s.close()}catch(_:Exception){}; listener.onPeerState(clients.isNotEmpty()) }
        }
    }
    fun send(m:String){ clients.toList().forEach{s->thread{try{PrintWriter(BufferedWriter(OutputStreamWriter(s.getOutputStream())),true).println(m)}catch(_:Exception){clients.remove(s)}}} }
    fun stop(){try{discovery?.let{nsd.stopServiceDiscovery(it)}}catch(_:Exception){};try{registration?.let{nsd.unregisterService(it)}}catch(_:Exception){};clients.forEach{try{it.close()}catch(_:Exception){}};try{server?.close()}catch(_:Exception){};try{multicastLock?.release()}catch(_:Exception){}}
}
