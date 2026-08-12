package com.cq.vibration.service.impl;

import com.cq.vibration.dto.TriAxisWaveform;
import com.cq.vibration.dto.WaveformDetail;
import com.cq.vibration.service.VibrationSignalProcessingService;
import com.cq.vibration.service.WaveformQueryService;
import com.cq.vibration.service.WaveformStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.util.Arrays;

/** 读取波形文件，并按统一口径计算速度与 FFT 单边 RMS 频谱。 */
@Service
public class WaveformQueryServiceImpl implements WaveformQueryService {
    private final JdbcTemplate jdbc;
    private final WaveformStorageService storage;
    private final VibrationSignalProcessingService processing;
    public WaveformQueryServiceImpl(JdbcTemplate jdbc, WaveformStorageService storage, VibrationSignalProcessingService processing) { this.jdbc = jdbc; this.storage = storage; this.processing = processing; }

    /** {@inheritDoc} */
    @Override public WaveformDetail getDetail(Long batchId, Integer downsample) {
        Meta meta = jdbc.query("""
                SELECT batch.collect_time,batch.waveform_path,equip.equip_code,equip.equip_name,sensor.sensor_code,sensor.mount_position
                FROM jzjc01_waveform_batch batch JOIN jzjc01_sensor_device sensor ON sensor.id=batch.point_id
                LEFT JOIN jzjc01_equipment equip ON equip.id=sensor.equipment_id WHERE batch.id=?
                """, (rs, row) -> new Meta(rs.getTimestamp(1).toLocalDateTime(),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6)), batchId).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "采集批次不存在"));
        TriAxisWaveform raw = storage.load(meta.path);
        double[] ax=processing.demean(raw.x()), ay=processing.demean(raw.y()), az=processing.demean(raw.z());
        double[] vx=processing.toVelocity(ax,raw.sampleRate()), vy=processing.toVelocity(ay,raw.sampleRate()), vz=processing.toVelocity(az,raw.sampleRate());
        int points=downsample==null?2000:Math.max(200,Math.min(downsample,10000));
        return new WaveformDetail(batchId,meta.time,meta.equipCode,meta.equipName,meta.sensorCode,meta.position,raw.sampleRate(),raw.x().length,
                time(ax,ay,az,raw.sampleRate(),points,"g"), time(vx,vy,vz,raw.sampleRate(),points,"mm/s"),
                spectrum(ax,ay,az,raw.sampleRate(),2000,"g RMS"), spectrum(vx,vy,vz,raw.sampleRate(),2000,"mm/s RMS"));
    }

    /** 对三轴时域数据采用同一索引降采样，保证轴间时间同步。 */
    private WaveformDetail.AxisWaveform time(double[] x,double[] y,double[] z,int rate,int limit,String unit) { int count=Math.min(x.length,limit); double[] t=new double[count],dx=new double[count],dy=new double[count],dz=new double[count]; for(int i=0;i<count;i++){int p=count==1?0:(int)Math.round((double)i*(x.length-1)/(count-1));t[i]=(double)p/rate;dx[i]=x[p];dy[i]=y[p];dz[i]=z[p];} return new WaveformDetail.AxisWaveform(t,dx,dy,dz,unit); }

    /** 使用 Hann 窗、单边 RMS 标定的基 2 FFT；频率上限为 min(5 kHz, 奈奎斯特频率)。 */
    private WaveformDetail.AxisSpectrum spectrum(double[] x,double[] y,double[] z,int rate,int limit,String unit) { int n=power(Math.min(x.length,16384)); Spec a=fft(x,n,rate),b=fft(y,n,rate),c=fft(z,n,rate); int last=0;while(last+1<a.f.length&&a.f[last+1]<=Math.min(5000D,rate/2D))last++;int count=Math.min(last+1,limit);double[] f=new double[count],dx=new double[count],dy=new double[count],dz=new double[count];for(int i=0;i<count;i++){int p=count==1?0:(int)Math.round((double)i*last/(count-1));f[i]=a.f[p];dx[i]=a.v[p];dy[i]=b.v[p];dz[i]=c.v[p];}return new WaveformDetail.AxisSpectrum(f,dx,dy,dz,unit); }

    /** 执行原地 Cooley-Tukey FFT，并输出每个频率 bin 的 RMS 值。 */
    private Spec fft(double[] input,int n,int rate){double[] r=Arrays.copyOf(input,n),im=new double[n];double energy=0;for(int i=0;i<n;i++){double w=.5-.5*Math.cos(2*Math.PI*i/(n-1));r[i]*=w;energy+=w*w;}for(int i=1,j=0;i<n;i++){int bit=n>>1;for(; (j&bit)!=0;bit>>=1)j^=bit;j^=bit;if(i<j){double q=r[i];r[i]=r[j];r[j]=q;q=im[i];im[i]=im[j];im[j]=q;}}for(int len=2;len<=n;len<<=1){double sr=Math.cos(-2*Math.PI/len),si=Math.sin(-2*Math.PI/len);for(int base=0;base<n;base+=len){double wr=1,wi=0;for(int k=0;k<len/2;k++){int l=base+k,h=l+len/2;double tr=r[h]*wr-im[h]*wi,ti=r[h]*wi+im[h]*wr;r[h]=r[l]-tr;im[h]=im[l]-ti;r[l]+=tr;im[l]+=ti;double nr=wr*sr-wi*si;wi=wr*si+wi*sr;wr=nr;}}}int bins=n/2+1;double[] f=new double[bins],v=new double[bins];double correction=Math.sqrt(energy/n);for(int k=0;k<bins;k++){double mag=Math.hypot(r[k],im[k])/n;double peak=(k==0||k==n/2?mag:2*mag)/correction;f[k]=(double)k*rate/n;v[k]=k==0?peak:peak/Math.sqrt(2);}return new Spec(f,v);}
    private int power(int value){int n=1;while(n*2<=value)n*=2;return n;}
    private record Spec(double[] f,double[] v){}
    private record Meta(java.time.LocalDateTime time,String path,String equipCode,String equipName,String sensorCode,String position){}
}
