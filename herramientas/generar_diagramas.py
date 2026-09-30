from pathlib import Path
import xml.etree.ElementTree as ET
import math, re, subprocess, json, shutil, os
from PIL import Image, ImageDraw, ImageFont
P=Path(__file__).resolve().parents[1]
OUT=P/'docs'
D=OUT/'diagramas'
D.mkdir(parents=True,exist_ok=True)
FONT='C:/Windows/Fonts/arial.ttf'
BOLD='C:/Windows/Fonts/arialbd.ttf'
SCALE=2
PAGES=[]


class Diagrama:
    def __init__(self, nombre, titulo, w, h, nota):
        self.nombre, self.w, self.h = nombre, w, h
        self.root = ET.Element('mxGraphModel', {'dx':str(w),'dy':str(h),'grid':'0','page':'1',
            'pageScale':'1','pageWidth':str(w),'pageHeight':str(h),'background':'#ffffff'})
        self.cells = ET.SubElement(self.root, 'root')
        ET.SubElement(self.cells, 'mxCell', id='0')
        ET.SubElement(self.cells, 'mxCell', id='1', parent='0')
        self.n = 1
        self.im = Image.new('RGB', (w*SCALE,h*SCALE), 'white')
        self.draw = ImageDraw.Draw(self.im)
        self.text(30,20,w-60,40,titulo,25,True)
        self.text(30,66,w-60,62,nota,17)
    def cell(self, value, style, x, y, w, h):
        self.n += 1
        c = ET.SubElement(self.cells,'mxCell',id=str(self.n),value=value,
            style=style,vertex='1',parent='1')
        ET.SubElement(c,'mxGeometry',x=str(x),y=str(y),width=str(w),height=str(h),attrib={'as':'geometry'})
        return str(self.n)
    def lines(self, text, width, size, bold=False):
        font = ImageFont.truetype(BOLD if bold else FONT,size*SCALE)
        result=[]
        for para in text.split('\n'):
            words=para.split(' '); line=''
            for word in words:
                candidate=(line+' '+word).strip()
                if line and self.draw.textlength(candidate,font=font)>width*SCALE:
                    result.append(line); line=word
                else: line=candidate
            result.append(line)
        return result
    def raster_text(self,x,y,w,h,text,size=17,bold=False,align='left'):
        font=ImageFont.truetype(BOLD if bold else FONT,size*SCALE)
        lines=self.lines(text,w-12,size,bold)
        if len(lines)*(size+5)>h+8:
            raise ValueError(f'Texto no cabe en {self.nombre}: {text} ({h})')
        for i,line in enumerate(lines):
            xx=x+6 if align=='left' else x+(w-self.draw.textlength(line,font=font)/SCALE)/2
            self.draw.text((xx*SCALE,(y+4+i*(size+5))*SCALE),line,font=font,fill='#152333')
    def text(self,x,y,w,h,text,size=17,bold=False,align='left'):
        self.cell(text, f'text;html=0;whiteSpace=wrap;align={align};verticalAlign=top;fontFamily=Arial;fontSize={size};fontColor=#152333;fontStyle={1 if bold else 0};spacing=6;',x,y,w,h)
        self.raster_text(x,y,w,h,text,size,bold,align)
    def box(self,x,y,w,h,text='',fill='white',size=17,bold=False,ellipse=False):
        color = '#ffffff' if fill=='white' else fill
        self.cell(text,f'{"ellipse;" if ellipse else "rounded=0;"}html=0;whiteSpace=wrap;align=center;verticalAlign=top;fontFamily=Arial;fontSize={size};fontColor=#152333;fontStyle={1 if bold else 0};spacing=6;fillColor={color};strokeColor=#374151;',x,y,w,h)
        coords=(x*SCALE,y*SCALE,(x+w)*SCALE,(y+h)*SCALE)
        if ellipse: self.draw.ellipse(coords,fill=color,outline='#374151',width=2*SCALE)
        else:self.draw.rectangle(coords,fill=color,outline='#374151',width=2*SCALE)
        if text: self.raster_text(x,y,w,h,text,size,bold,'center')
    def edge(self,pts,label='',dashed=False,arrow=True,size=17,labelpos=None,open_arrow=False,hollow=False):
        self.n+=1
        c=ET.SubElement(self.cells,'mxCell',id=str(self.n),value='',edge='1',parent='1',style=f'edgeStyle=none;rounded=0;html=0;endArrow={"open" if open_arrow else "block" if arrow else "none"};endFill={0 if open_arrow or hollow else 1};dashed={1 if dashed else 0};strokeColor=#374151;strokeWidth=1.5;')
        geo=ET.SubElement(c,'mxGeometry',relative='1',attrib={'as':'geometry'})
        for pt,kind in [(pts[0],'sourcePoint'),(pts[-1],'targetPoint')]:ET.SubElement(geo,'mxPoint',x=str(pt[0]),y=str(pt[1]),attrib={'as':kind})
        if len(pts)>2:
            a=ET.SubElement(geo,'Array',attrib={'as':'points'})
            for x,y in pts[1:-1]:ET.SubElement(a,'mxPoint',x=str(x),y=str(y))
        for (x1,y1),(x2,y2) in zip(pts,pts[1:]):
            length=math.hypot(x2-x1,y2-y1)
            if dashed:
                for i in range(0,int(length),12):
                    t1=i/max(length,1);t2=min(i+6,length)/max(length,1)
                    self.draw.line(((x1+(x2-x1)*t1)*SCALE,(y1+(y2-y1)*t1)*SCALE,
                         (x1+(x2-x1)*t2)*SCALE,(y1+(y2-y1)*t2)*SCALE),fill='#374151',width=2*SCALE)
            else:self.draw.line((x1*SCALE,y1*SCALE,x2*SCALE,y2*SCALE),fill='#374151',width=2*SCALE)
        if arrow:
            x,y=pts[-1];a,b=pts[-2];ang=math.atan2(y-b,x-a)
            tri=[(x*SCALE,y*SCALE)]+[((x-12*math.cos(ang+q))*SCALE,(y-12*math.sin(ang+q))*SCALE) for q in (-.4,.4)]
            if open_arrow:self.draw.line([tri[1],tri[0],tri[2]],fill='#374151',width=2*SCALE)
            elif hollow:self.draw.polygon(tri,fill='white',outline='#374151',width=2*SCALE)
            else:self.draw.polygon(tri,fill='#374151')
        if label:
            x,y,w=labelpos if labelpos else (min(pts[0][0],pts[-1][0])+8,pts[0][1]-30,max(abs(pts[-1][0]-pts[0][0])-16,250))
            self.text(x,y,w,48,label,size)
    def actor(self,x,y):
        self.cell('Orquita','shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=0;fontFamily=Arial;fontSize=18;',x-20,y,40,70)
        self.draw.ellipse(((x-10)*SCALE,y*SCALE,(x+10)*SCALE,(y+20)*SCALE),outline='#374151',width=2*SCALE)
        for a,b,c,d in [(x,y+20,x,y+45),(x-20,y+30,x+20,y+30),(x,y+45,x-20,y+70),(x,y+45,x+20,y+70)]:
            self.draw.line((a*SCALE,b*SCALE,c*SCALE,d*SCALE),fill='#374151',width=2*SCALE)
        self.raster_text(x-50,y+73,100,30,'Orquita',18,False,'center')
    def frame(self,x,y,w,h,titulo):
        self.cell('', 'rounded=0;fillColor=none;strokeColor=#374151;',x,y,w,h)
        self.draw.rectangle((x*SCALE,y*SCALE,(x+w)*SCALE,(y+h)*SCALE),outline='#374151',width=2*SCALE)
        self.box(x,y,min(w,650),36,titulo,'#edf2f7',16)
    def save(self):
        mf=ET.Element('mxfile',host='app.diagrams.net',version='24.7.17')
        d=ET.SubElement(mf,'diagram',id=self.nombre,name=self.nombre)
        d.append(self.root)
        ET.ElementTree(mf).write(D/(self.nombre+'.drawio'),encoding='utf-8',xml_declaration=True)
        self.im.resize((self.w,self.h),Image.Resampling.LANCZOS).save(D/(self.nombre+'.png'))
        PAGES.append((self.nombre,self.root))

def seq(nombre,titulo,nombres,h,nota):
    centro=max(1450,280*len(nombres)+110)
    w=centro+480
    d=Diagrama(nombre,titulo,w,h,nota)
    inicio=70 if nombres[0]=='Orquita' else 150
    xs={n:inicio+i*(centro-70-inicio)/(len(nombres)-1) for i,n in enumerate(nombres)}
    for n,x in xs.items():
        if n=='Orquita':d.actor(x,130)
        else:d.box(x-125,150,250,62,n,'#edf2f7',17,True)
        d.edge([(x,240),(x,h-65)],dashed=True,arrow=False)
    d.xs=xs
    return d

def msg(d,a,b,y,label,dashed=False):
    xa,xb=d.xs[a],d.xs[b]
    if a==b:
        d.edge([(xa,y),(xa+75,y),(xa+75,y+26),(xa,y+26)],dashed=dashed,
            label=label,labelpos=(xa+10,y-33,430))
    else:d.edge([(xa,y),(xb,y)],label,dashed=dashed)

def clase(d,n,x,y,w=585):
    c=CLASSES[n];header=n+(' «Thread»' if n in ['ActualizadorEstomago','LectorEstomago'] else ' «enum»' if n in ['ModoOperativo','Movimiento','EntornoClasificado'] else '')
    attr='\n'.join(c['atributos']) or '(sin atributos declarados)'
    met='\n'.join(c['metodos']) or '(constantes del enum)'
    ha=max(50,len(d.lines(attr,w-14,16))*21+16)
    hm=max(50,len(d.lines(met,w-14,16))*21+16)
    d.box(x,y,w,42,header,'#e4eef8',19,True)
    d.box(x,y+42,w,ha);d.text(x+5,y+47,w-10,ha-6,attr,16)
    d.box(x,y+42+ha,w,hm);d.text(x+5,y+47+ha,w-10,hm-6,met,16)
    return (x,y,w,42+ha+hm)

d=seq('03_Secuencia_CU09','CU-09 — Actualizar estómago en el hilo escritor',
 ['Orquita','Recopilador','ReceptorCapturas','ActualizadorEstomago','ArchivoEstomago'],1370,
 'El arranque ejecuta actualizador.start() una vez; la JVM invoca run(). Cada mensaje encola una captura y despierta ese hilo.')
d.frame(790,581,1155,740,'loop — run(): esperar o procesar la siguiente captura')
d.frame(835,624,1100,536,'critical — permiso exclusivo sobre estomago.txt')
d.frame(863,793,1050,220,'alt — id nuevo / duplicado / IOException')
d.frame(44,250,470,58,'ref CU-06 — captura recibida por TCP')
msg(d,'Recopilador','ReceptorCapturas',350,'notificarCaptura(avisoCaptura)')
msg(d,'ReceptorCapturas','ActualizadorEstomago',413,'encolarCaptura(captura)')
msg(d,'ActualizadorEstomago','ArchivoEstomago',475,'anunciarEscritura()')
d.text(825,517,560,80,'Cola synchronized: addLast(captura) y notifyAll(). Nuevas lecturas esperan mientras escriturasPendientes > 0.',16)
msg(d,'ActualizadorEstomago','ArchivoEstomago',710,'escribirRegistro(captura)')
msg(d,'ArchivoEstomago','ArchivoEstomago',767,'mutex.acquire()')
d.text(880,837,1020,45,'[id nuevo] leerDelArchivo(), escribir el registro UTF-8 y cerrar el archivo.',17)
d.edge([(863,888),(1913,888)],dashed=True,arrow=False)
d.text(880,893,1020,45,'[id duplicado] no agregar una segunda línea.',17)
d.edge([(863,945),(1913,945)],dashed=True,arrow=False)
d.text(880,950,1020,45,'[IOException] no confirmar la captura; propagar después del finally.',17)
msg(d,'ArchivoEstomago','ArchivoEstomago',1080,'finally: mutex.release()')
msg(d,'ArchivoEstomago','ActualizadorEstomago',1124,'boolean / excepción',True)
msg(d,'ActualizadorEstomago','ArchivoEstomago',1220,'finally: finalizarEscritura()')
d.text(545,1260,860,58,'El escritor vuelve a esperar o procesa la siguiente captura. Al llegar a cero pendientes, notifyAll() habilita al lector.',17)
d.save()

d=seq('04_Secuencia_CU10','CU-10 — Leer estómago con LOOP y CRITICAL',
 ['Orquita','ActualizadorEstomago','ArchivoEstomago','LectorEstomago','ActivadorModoEscape'],1590,
 'El auditor se inicia con lector.start() y continúa sin nuevos mensajes. Orquita es el actor asociado que origina las capturas; no invoca run().')
d.frame(32,268,1900,1245,'par — escritor y lector independientes')
d.edge([(32,475),(1932,475)],dashed=True,arrow=False)
d.frame(46,328,625,58,'ref CU-06 y CU-09 — recepción y encolado de captura')
d.text(355,390,1025,62,'CU-09 escribe con el mismo semáforo. Si una lectura ya comenzó, la escritura espera; ninguna lectura nueva adelanta una captura pendiente.',17)
d.frame(580,501,1330,987,'loop — sistemaActivo() == true')
d.frame(599,562,805,460,'critical — lectura exclusiva del archivo')
msg(d,'LectorEstomago','ArchivoEstomago',638,'leerRegistros()')
d.text(620,679,737,80,'Admisión synchronized: wait() mientras hay escrituras pendientes. Luego mutex.acquire(). Si el archivo no existe, devolver lista vacía.',17)
msg(d,'ArchivoEstomago','ArchivoEstomago',814,'leerDelArchivo()')
msg(d,'ArchivoEstomago','ArchivoEstomago',885,'finally: mutex.release()')
msg(d,'ArchivoEstomago','LectorEstomago',970,'registros válidos y únicos',True)
msg(d,'LectorEstomago','LectorEstomago',1063,'contarRegistrosValidos(registros)')
d.frame(952,1120,930,195,'opt — cantidad ≥ 3 y !modoEscapeNotificado')
msg(d,'LectorEstomago','ActivadorModoEscape',1218,'notificarCapturasCompletas(evento)')
d.text(989,1250,390,50,'ref CU-11; después marcar notificado',16)
msg(d,'LectorEstomago','LectorEstomago',1380,'esperarProximoCiclo(intervaloAuditoria)')
d.text(90,1521,1310,57,'IOException: registrar error, no contar ni notificar; reintentar el próximo ciclo. Interrupción de cierre: terminar run(). El semáforo se libera en finally.',17)
d.save()

d=seq('05_Secuencia_CU11','CU-11 — Activar modo escape',
 ['Orquita','LectorEstomago','ActivadorModoEscape','Navegador'],1050,
 'Orquita participa a través de sus capturas persistidas. ESCAPE cambia el modo interno. Las órdenes TCP siguen usando evitación; todavía no se localiza una salida física.')
d.frame(46,269,630,58,'ref CU-06 / CU-09 / CU-10 — capturas auditadas')
msg(d,'LectorEstomago','ActivadorModoEscape',365,'notificarCapturasCompletas(evento)')
d.frame(785,410,613,470,'alt — cantidad < 3 / cantidad ≥ 3')
msg(d,'ActivadorModoEscape','ActivadorModoEscape',497,'mantenerModoBusqueda()')
d.edge([(785,581),(1398,581)],dashed=True,arrow=False)
msg(d,'ActivadorModoEscape','ActivadorModoEscape',653,'modoActual = cambiarModo(ESCAPE)')
msg(d,'ActivadorModoEscape','Navegador',747,'establecerModoOperativo(modoActual)')
d.text(804,795,565,58,'El modo queda en ESCAPE y no se revierte por un aviso tardío.',17)
d.text(320,918,1062,67,'Contrato interno: EventoModoEscape(tipoEvento, cantidadCapturas, timestamp). Un tipo inválido produce IllegalArgumentException. Un aviso repetido en ESCAPE no cambia el estado.',17)
d.save()




# CU-06: network framing, complete validation and dispatch.
d=seq('02_Secuencia_CU06','CU-06 — Recibir recopilación y despachar',
 ['Orquita','ReceptorIngestaTCP','ComunicadorOrquita','PaqueteRecopilacion','Recopilador','ReceptorCapturas'],1370,
 'Recopilación contiene 12 distancias y captura opcional. CAPTURA legado conserva su formato y no produce orden. Actor externo simulado.')
msg(d,'Orquita','ReceptorIngestaTCP',300,'RECOPILACION|fecha|distancias|id + LF')
msg(d,'ReceptorIngestaTCP','ComunicadorOrquita',365,'enviarRecopilacion(mensaje)')
msg(d,'ComunicadorOrquita','PaqueteRecopilacion',430,'decodificar(mensaje)')
msg(d,'PaqueteRecopilacion','ComunicadorOrquita',510,'paquete validado / excepción',True)
d.frame(30,560,1670,127,'break — formato inválido: enviarErrorTrama() y retornar sin persistencia')
msg(d,'ComunicadorOrquita','Orquita',643,'ERROR|TRAMA_INVALIDA + LF')
msg(d,'ComunicadorOrquita','Recopilador',740,'procesarRecopilacion(paquete)')
msg(d,'Recopilador','PaqueteRecopilacion',790,'esValido()')
msg(d,'Recopilador','Recopilador',850,'extraerTelemetria(paquete); extraerAvisoCaptura(paquete)')
d.frame(1190,905,825,133,'opt — avisoCaptura != null')
msg(d,'Recopilador','ReceptorCapturas',985,'notificarCaptura(aviso) — ref CU-09')
d.frame(1160,1050,780,110,'opt — telemetria != null: ref CU-07')
d.text(1180,1090,730,58,'determinarMovimiento(telemetria) devuelve Movimiento',17)
msg(d,'Recopilador','ComunicadorOrquita',1220,'movimiento / null (CAPTURA legado)',True)
d.text(580,1270,1350,50,'Si movimiento != null: ComunicadorOrquita continúa CU-08 en la misma conexión. La captura puede seguir pendiente en el escritor.',17)
d.save()

d=seq('08_Secuencia_CU07','CU-07 — Determinar movimiento con Neuroph',
 ['Orquita','Recopilador','Navegador','ClasificadorEntorno'],1480,
 'Actor asociado mediante ref CU-06. Se normalizan las distancias; la clasificación se obtiene por inferencia de una red entrenada.')
d.frame(35,263,600,57,'ref CU-06 — telemetría enviada por Orquita')
msg(d,'Recopilador','Navegador',380,'determinarMovimiento(telemetria)')
msg(d,'Navegador','ClasificadorEntorno',450,'clasificarEntorno(telemetria)')
d.text(1280,486,590,100,'synchronized: red.setInput(telemetria.normalizar()); red.calculate(); red.getOutput(). Salida mayor: LIBRE / OBJETO.',16)
msg(d,'ClasificadorEntorno','Navegador',630,'entornoClasificado',True)
d.frame(765,686,1080,560,'alt — entorno == null / clasificación disponible')
msg(d,'Navegador','Navegador',780,'mantenerUltimaDecision()')
d.edge([(765,856),(1845,856)],dashed=True,arrow=False)
msg(d,'Navegador','Navegador',940,'modoActual = obtenerModoOperativo()')
msg(d,'Navegador','Navegador',1050,'calcularMovimiento(entorno, modoActual, telemetria)')
d.text(805,1110,1000,90,'LIBRE: AVANZAR. OBJETO: ambas sumas < 1200 → DETENER; si izquierda >= derecha → GIRAR_IZQ; en otro caso GIRAR_DER. Guardar ultimaDecision.',17)
msg(d,'Navegador','Recopilador',1320,'Movimiento',True)
d.text(420,1372,1360,60,'Retorno por CU-06 al comunicador, que ejecuta CU-08. El modo ESCAPE se conserva; la búsqueda de salida física está pendiente.',17)
d.save()

d=seq('09_Secuencia_CU08','CU-08 — Enviar orden por la conexión de origen',
 ['ComunicadorOrquita','Writer','Orquita'],1050,
 'Precondición: CU-06 recibió Movimiento desde CU-07. Writer representa el flujo de salida del socket de esa Orquita.')
d.frame(35,264,620,60,'ref CU-06 / CU-07 — movimiento disponible')
msg(d,'ComunicadorOrquita','ComunicadorOrquita',395,'enviarOrdenNavegacion(movimiento)')
msg(d,'ComunicadorOrquita','ComunicadorOrquita',490,'codificarOrden(movimiento)')
d.frame(35,552,1390,414,'alt — envío correcto / IOException')
msg(d,'ComunicadorOrquita','Writer',610,'write(mensaje con LF); flush()')
msg(d,'Writer','Orquita',715,'ORDEN|movimiento + LF (TCP)')
d.edge([(35,775),(1425,775)],dashed=True,arrow=False)
d.text(55,785,1260,40,'[IOException] al escribir o vaciar el flujo',18,True)
d.text(65,830,1240,90,'Propagar al manejador TCP; registrar y cerrar esa conexión. El servidor acepta conexiones nuevas. No reenviar la orden a otro socket. El cliente debe reconectar y mandar una lectura nueva.',18)
d.save()

d=Diagrama('01_Casos_de_uso','Casos de uso — Hito 2 preliminar del servidor',1860,1040,
 'Orquita es el actor externo. CU-07 se alcanza desde CU-06. CU-09/CU-10 son hilos independientes; el cambio a ESCAPE aún no localiza la salida.')
d.actor(95,450);d.box(280,150,1500,800)
d.text(315,162,1400,40,'Servidor Java — demostración TCP y Neuroph',23,True,'center')
for x,y,label in [(550,320,'CU-06\nRecibir recopilación'),(1030,320,'CU-07\nDeterminar movimiento'),(1510,320,'CU-08\nEnviar orden'),(550,745,'CU-09\nActualizar estómago'),(1030,745,'CU-10\nLeer estómago'),(1510,745,'CU-11\nActivar escape')]:
 d.box(x-140,y-55,280,120,label,'white',18,False,True)
for pts in [[(120,490),(210,490),(210,325),(410,325)],[(120,490),(230,490),(230,915),(1030,915),(1030,810)],[(120,490),(180,490),(180,715),(410,745)],[(120,490),(250,490),(250,220),(1510,220),(1510,265)]]: d.edge(pts,arrow=False)
d.edge([(690,325),(890,325)],'«include»',True,labelpos=(705,277,185),open_arrow=True)
d.edge([(550,690),(550,385)],'«extend» [captura]',True,labelpos=(315,475,220),open_arrow=True)
d.edge([(1370,750),(1170,750)],'«extend»',True,labelpos=(1182,701,182),open_arrow=True)
d.text(1175,798,210,76,'[3 capturas y aún no notificado]',16)
d.edge([(650,385),(650,560),(1510,560),(1510,385)],dashed=True,open_arrow=True)
d.text(910,525,340,45,'«include» CU-08 si hay telemetría',16)
d.text(720,610,740,68,'CU-09 y CU-10 comparten estomago.txt; Semaphore impide leer y escribir simultáneamente.',18)
d.save()

# Extract all server fields/method signatures from bytecode; no hand-invented signatures.
CLASSES={}
javap=shutil.which('javap')
if not javap: raise RuntimeError('Se requiere javap del JDK en PATH')
for f in sorted((P/'src/main/java/orquitas/servidor').rglob('*.java')):
 package=re.search(r'package ([\w.]+);',f.read_text(encoding='utf-8')).group(1)
 result=subprocess.check_output([javap,'-private','-classpath',str(P/'target/classes'),package+'.'+f.stem],text=True,encoding='utf-8')
 attrs=[];methods=[]
 for raw in result.splitlines():
  s=raw.strip()
  if not s.endswith(';') or '$' in s or not re.match(r'(public|private|protected) ',s):continue
  static=' static ' in (' '+s+' ')
  vis='+' if s.startswith('public') else '-' if s.startswith('private') else '#'
  s=re.sub(r'\b(?:[a-z]\w*\.)+([A-Z]\w*)',r'\1',s)
  s=re.sub(r'^(?:public|private|protected)\s+','',s)
  s=re.sub(r'\b(?:final|static|volatile|synchronized)\s+','',s).rstrip(';')
  if '(' in s:
   s=s.split(' throws ')[0];pre,args=s.split('(',1);words=pre.split(' ')
   m=words[-1]+'('+args
   if len(words)>1:m+=' : '+' '.join(words[:-1])
   methods.append(vis+' '+m+(' {static}' if static else ''))
  else:
   tipo,n=s.rsplit(' ',1);attrs.append(vis+' '+n+' : '+tipo+(' {static}' if static else ''))
 CLASSES[f.stem]={'atributos':attrs,'metodos':methods,'fuente':f.relative_to(P).as_posix(),'paquete':package}
(OUT/'firmas-verificadas.json').write_text(json.dumps(CLASSES,ensure_ascii=False,indent=2),encoding='utf-8')

groups=[('10_Clases_navegacion',['Navegador','ClasificadorEntorno','Telemetria','Movimiento','EntornoClasificado','ModoOperativo']),
 ('11_Clases_comunicacion',['ReceptorIngestaTCP','ComunicadorOrquita','Recopilador','PaqueteRecopilacion']),
 ('12_Clases_persistencia',['ReceptorCapturas','ActualizadorEstomago','ArchivoEstomago','LectorEstomago','ActivadorModoEscape','EventoModoEscape']),
 ('13_Clases_datos_arranque',['Captura','AvisoCaptura','ServidorOrquita'])]
relations=[('Navegador','ClasificadorEntorno'),('ClasificadorEntorno','Telemetria'),('Navegador','ModoOperativo'),('Navegador','Movimiento'),('ClasificadorEntorno','EntornoClasificado'),('ReceptorIngestaTCP','Recopilador'),('ReceptorIngestaTCP','ComunicadorOrquita'),('ComunicadorOrquita','Recopilador'),('Recopilador','PaqueteRecopilacion'),('ReceptorCapturas','ActualizadorEstomago'),('ActualizadorEstomago','ArchivoEstomago'),('LectorEstomago','ArchivoEstomago'),('LectorEstomago','ActivadorModoEscape'),('ActivadorModoEscape','EventoModoEscape')]
for nombre,nombres in groups:
 d=Diagrama(nombre,'Clases del servidor — '+nombre.split('_',2)[2],2400,2310,
  'Firmas verificadas con javap. + público; - privado; {static} estático. Tipos de parámetros abreviados. Relaciones de dependencia con flecha discontinua.')
 pos={}
 for i,n in enumerate(nombres): pos[n]=clase(d,n,40+(i%3)*790,165+(i//3)*820,735)
 y=1850
 for a,b in relations:
  if a in nombres and b in nombres:
   d.text(60,y,2250,35,a+' → '+b,17);y+=31
 d.text(60,2220,2250,52,'Relaciones entre vistas: Recopilador → Navegador / ReceptorCapturas; PaqueteRecopilacion → Telemetria / AvisoCaptura; ActivadorModoEscape → Navegador.',16)
 if 'ActualizadorEstomago' in nombres:
  d.text(65,1780,2230,60,'ActualizadorEstomago y LectorEstomago heredan Thread. Ambos comparten una única instancia de ArchivoEstomago. La cola coordina; no cuenta capturas.',17)
 if 'ActualizadorEstomago' in nombres:
  d.box(870,1600,410,78,'Thread «Java»\n+ run() : void',size=18)
  for n in ['ActualizadorEstomago','LectorEstomago']:
   x,y,w,h=pos[n]
   if n=='ActualizadorEstomago': d.edge([(x+w,y+180),(1590,y+180),(1590,1710),(1190,1710),(1190,1678)],hollow=True)
   else: d.edge([(x+w/2,y+h),(x+w/2,1710),(980,1710),(980,1678)],hollow=True)
 # Actual arrows for neighboring classes, routed in whitespace.
 for a,b in relations:
  if a in pos and b in pos:
   ax,ay,aw,ah=pos[a];bx,by,bw,bh=pos[b]
   if ay==by and abs(ax-bx)<800:
    if ax<bx:d.edge([(ax+aw,ay+80),(bx,by+80)],dashed=True,open_arrow=True)
    else:d.edge([(ax,ay+110),(bx+bw,by+110)],dashed=True,open_arrow=True)
   elif ay<by:
    d.edge([(ax+aw/2,ay+ah),(ax+aw/2,865),(bx+bw/2,865),(bx+bw/2,by)],dashed=True,open_arrow=True)
 d.save()

mf=ET.Element('mxfile',host='app.diagrams.net',version='24.7.17')
for name,root in sorted(PAGES):ET.SubElement(mf,'diagram',id=name,name=name).append(root)
ET.ElementTree(mf).write(D/'Orquitas_Hito2.drawio',encoding='utf-8',xml_declaration=True)
md=['# Clases vigentes — Hito 2 preliminar\n','Firmas extraídas del bytecode Java compilado. Referencia: `diagramas/Orquitas_Hito2.drawio`. Las firmas del documento recibido fueron actualizadas con las decisiones explicadas en README.\n']
for n,c in CLASSES.items():md+=['## '+n+'\n','Fuente: `'+c['fuente']+'`.\n','```text\n'+'\n'.join(c['atributos']+c['metodos'])+'\n```\n']
md+=['## Relaciones\n']+['- '+a+' → '+b for a,b in relations]+['- Recopilador → Navegador / ReceptorCapturas','- PaqueteRecopilacion → Telemetria / AvisoCaptura','- ActivadorModoEscape → Navegador','- ActualizadorEstomago y LectorEstomago heredan Thread y comparten ArchivoEstomago.','- ServidorOrquita construye el grafo y ejecuta start() en ambos hilos.','- ClasificadorEntorno posee NeuralNetwork; carga el modelo y sincroniza la inferencia.','- El cliente y el entrenador son simulación; no integran el diseño POO del servidor.']
(OUT/'Orquitas_Diagrama_Clases_Modulo_Servidor.md').write_text('\n'.join(md),encoding='utf-8')
print('Generados',len(PAGES),'diagramas y firmas de',len(CLASSES),'clases')
