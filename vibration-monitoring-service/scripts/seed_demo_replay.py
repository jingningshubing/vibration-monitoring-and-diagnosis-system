import csv, gzip, json, math, statistics, glob
from datetime import datetime, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATASET = ROOT / '感应电机在不同负载条件下的三轴轴承振动数据集' / 'fm6xzxnf36-2'
OUT = ROOT / 'vibration-monitoring-backend' / 'data' / 'waveforms'
SQL = ROOT / 'vibration-monitoring-backend' / 'data' / 'seed_demo_replay_20260731.sql'
DAY = datetime(2026, 7, 31)
SAMPLES = 10000

PLAN = {
 'V-01': ('CA-PUMP', 'CA 泵电机驱动端轴承', [
  'Healthy bearing data/healthy without pulley.csv', '0.7mm-bearing-faults/0.7inner-100watt-*.csv', '1.1mm-bearing-faults/1.1inner-200watt.csv', '1.7mm-bearing-faults/1.7inner-300watt.csv']),
 'V-02': ('CA-PUMP', 'CA 泵泵端轴承', [
  'Healthy bearing data/Healthy with pulley.csv', '0.7mm-bearing-faults/0.7outer-100watt-*.csv', '1.1mm-bearing-faults/1.1outer-200watt.csv', '1.7mm-bearing-faults/1.7outer-300watt.csv']),
 'V-03': ('CB-PUMP', 'CB 泵电机驱动端轴承', [
  'Healthy bearing data/healthy without pulley.csv', '0.9mm-bearing-faults/0.9inner-100watt.csv', '1.3mm-bearing-faults/1.3inner-200watt.csv', '1.5mm-bearing-faults/1.5inner-300watt.csv']),
 'V-04': ('CB-PUMP', 'CB 泵泵端轴承', [
  'Healthy bearing data/Healthy with pulley.csv', '0.9mm-bearing-faults/0.9outer-100watt.csv', '1.3mm-bearing-faults/1.3outer-200watt.csv', '1.5mm-bearing-faults/1.5outer-300watt.csv'])
}
LEVELS = ['NORMAL', 'WARNING', 'ALARM', 'DANGER']

def resolve(pattern):
    hits = glob.glob(str(DATASET / pattern))
    if len(hits) != 1: raise RuntimeError(f'找不到唯一文件: {pattern}')
    return Path(hits[0])

def read_batches(path):
    rows=[]
    with path.open(encoding='utf-8-sig', newline='') as f:
        for r in csv.DictReader(f): rows.append((float(r[' X-axis']), float(r[' Y-axis']), float(r[' Z-axis'])))
    return [rows[i:i+SAMPLES] for i in range(0, len(rows)-SAMPLES+1, SAMPLES)]

def velocity_rms(a):
    mean=sum(a)/len(a); dt=1/10000; v=[]; cur=0.0
    for x in a:
        cur += (x-mean)*9.80665*1000*dt; v.append(cur)
    slope=(v[-1]-v[0])/(len(v)-1)
    v=[x-(v[0]+i*slope) for i,x in enumerate(v)]
    return math.sqrt(sum(x*x for x in v)/len(v))

def rms(a): return math.sqrt(sum(x*x for x in a)/len(a))
def esc(s): return str(s).replace('\\','\\\\').replace("'", "''")

lines=['SET NAMES utf8mb4;', 'DELETE f FROM jzjc01_vibration_feature f JOIN jzjc01_waveform_batch b ON f.batch_id=b.id WHERE b.collect_time >= \'2026-07-31 00:00:00\' AND b.collect_time < \'2026-08-01 00:00:00\';', 'DELETE a FROM jzjc01_alarm_record a WHERE a.alarm_time >= \'2026-07-31 00:00:00\' AND a.alarm_time < \'2026-08-01 00:00:00\';', 'DELETE FROM jzjc01_waveform_batch WHERE collect_time >= \'2026-07-31 00:00:00\' AND collect_time < \'2026-08-01 00:00:00\';']
for code,(device,name,patterns) in PLAN.items():
    lines.append(f"SET @point_id=(SELECT id FROM jzjc01_sensor_device WHERE sensor_code='{code}'); UPDATE jzjc01_sensor_device SET sampling_rate=10000,current_status='NORMAL',last_collect_time='2026-07-31 23:00:00' WHERE id=@point_id;")
    source_batches=[read_batches(resolve(x)) for x in patterns]
    normal=[velocity_rms([r[k] for r in b]) for b in source_batches[0][:6] for k in range(3)]
    threshold=max(normal)*1.05
    lines.append(f"UPDATE jzjc01_sensor_device SET threshold_value={threshold:.6f} WHERE id=@point_id;")
    for hour in range(24):
        stage=hour//6; batch=source_batches[stage][hour%6]; axes=[[r[k] for r in batch] for k in range(3)]
        vr=[velocity_rms(a) for a in axes]; ar=[rms(a) for a in axes]; total=max(vr); axis='XYZ'[vr.index(total)]; time=DAY+timedelta(hours=hour)
        rel=Path(code)/'2026-07-31'/f'{time:%Y%m%d_%H%M%S}.json.gz'; target=OUT/rel; target.parent.mkdir(parents=True,exist_ok=True)
        payload={'sensorCode':code,'collectTime':time.isoformat(sep=' '),'sampleRate':10000,'sampleCount':10000,'unit':'g','axes':{'x':axes[0],'y':axes[1],'z':axes[2]},'source':{'type':'PUBLIC_DATASET_DEMO','file':resolve(patterns[stage]).name,'startRow':hour%6*SAMPLES+2,'plannedLevel':LEVELS[stage]}}
        with gzip.open(target,'wt',encoding='utf-8') as f: json.dump(payload,f,separators=(',',':'))
        path=str(Path('data')/'waveforms'/rel).replace('\\','/')
        lines.append(f"INSERT INTO jzjc01_waveform_batch(point_id,collect_time,sample_rate,sample_count,data_format,waveform_path,detection_status,diagnosis_status) VALUES (@point_id,'{time:%F %T}',10000,10000,'JSON_GZIP_3AXIS','{path}','{LEVELS[stage]}','NOT_REQUIRED'); SET @batch_id=LAST_INSERT_ID();")
        lines.append(f"INSERT INTO jzjc01_vibration_feature(batch_id,rms,peak,peak_to_peak,crest_factor,x_acceleration_rms,y_acceleration_rms,z_acceleration_rms,x_velocity_rms,y_velocity_rms,z_velocity_rms,total_vibration,max_axis) VALUES (@batch_id,{total:.6f},0,0,0,{ar[0]:.6f},{ar[1]:.6f},{ar[2]:.6f},{vr[0]:.6f},{vr[1]:.6f},{vr[2]:.6f},{total:.6f},'{axis}');")
        if stage:
            label={'WARNING':'预警','ALARM':'报警','DANGER':'危险'}[LEVELS[stage]]
            lines.append(f"INSERT INTO jzjc01_alarm_record(batch_id,point_id,alarm_level,alarm_type,alarm_value,message,alarm_time,status) VALUES (@batch_id,@point_id,'{LEVELS[stage]}','总振值{label}',{total:.6f},'公开数据集回放：当前总振值 {total:.3f} mm/s RMS，状态：{label}','{time:%F %T}','ACTIVE');")
SQL.parent.mkdir(parents=True,exist_ok=True); SQL.write_text('\n'.join(lines)+'\n',encoding='utf-8')
print(f'created {SQL}')
