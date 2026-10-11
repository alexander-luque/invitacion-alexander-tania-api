package pe.at.invitacion;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

@RestController @RequestMapping("/api")
public class Api {
  private final JdbcTemplate db;
  private final String adminKey, frontendOrigin, publicBase;
  public Api(JdbcTemplate db,@Value("${app.admin-key}") String adminKey,@Value("${app.frontend-origin}") String frontendOrigin,@Value("${app.public-base-url}") String publicBase){this.db=db;this.adminKey=adminKey;this.frontendOrigin=frontendOrigin;this.publicBase=publicBase;}
  public record Invitado(String codigo,String nombre,int cupos,String estado,String mensaje,int ingresoCupos){}
  public record Rsvp(@NotNull Boolean asistira,@Size(max=1500) String mensaje){}
  public record Aporte(@NotNull @DecimalMin("0.01") BigDecimal monto,@NotNull LocalDate fechaDeposito){}
  public record Cupos(@Min(0) int cupos){}
  public record Entrada(@Min(1) int cantidad){}
  public record EstadoAporte(@Pattern(regexp="PENDIENTE|VERIFICADO|RECHAZADO") String estado){}
  private void admin(String key){if(adminKey.isBlank() || key==null || !MessageDigest.isEqual(adminKey.getBytes(StandardCharsets.UTF_8),key.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Credencial administrativa inválida");}
  private Invitado get(String code){var rows=db.query("SELECT codigo,nombre,cupos,estado,mensaje,ingreso_cupos FROM invitado WHERE codigo=?",(rs,i)->new Invitado(rs.getString(1),rs.getString(2),rs.getInt(3),rs.getString(4),rs.getString(5),rs.getInt(6)),code);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Invitación no encontrada");return rows.get(0);}
  @GetMapping("/invitaciones/{codigo}") public Map<String,Object> invitacion(@PathVariable String codigo){Invitado i=get(codigo);return Map.of("codigo",i.codigo(),"nombre",i.nombre(),"cuposAsignados",i.cupos()>0,"puedeConfirmar",i.cupos()>0,"estado",i.estado());}
  @PostMapping("/invitaciones/{codigo}/rsvp") @Transactional public Map<String,Object> rsvp(@PathVariable String codigo,@Valid @RequestBody Rsvp r){Invitado i=get(codigo);if(i.cupos()==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Cupos pendientes de asignación");db.update("UPDATE invitado SET estado=?,mensaje=?,actualizado_en=now() WHERE codigo=?",r.asistira()?"SI":"NO",r.mensaje(),codigo);return Map.of("ok",true,"estado",r.asistira()?"SI":"NO");}
  @GetMapping("/invitaciones/{codigo}/pase") public Map<String,Object> pase(@PathVariable String codigo){Invitado i=get(codigo);if(i.cupos()==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Cupos pendientes de asignación");String token=db.queryForObject("SELECT pase_token::text FROM invitado WHERE codigo=?",String.class,codigo);return Map.of("nombre",i.nombre(),"codigo",codigo,"cupos",i.cupos(),"qrUrl",publicBase+"/api/pases/"+token+"/qr");}
  @GetMapping(value="/pases/{token}/qr",produces=MediaType.IMAGE_PNG_VALUE) public byte[] qr(@PathVariable UUID token){Integer n=db.queryForObject("SELECT count(*) FROM invitado WHERE pase_token=? AND cupos>0",Integer.class,token);if(n==null||n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);try{var hints=Map.of(EncodeHintType.MARGIN,2);var matrix=new QRCodeWriter().encode(publicBase+"/api/pases/"+token+"/verificar",BarcodeFormat.QR_CODE,360,360,hints);var out=new ByteArrayOutputStream();MatrixToImageWriter.writeToStream(matrix,"PNG",out);return out.toByteArray();}catch(Exception e){throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"No se pudo generar QR");}}
  @PostMapping("/invitaciones/{codigo}/aportes") public Map<String,Object> aporte(@PathVariable String codigo,@Valid @RequestBody Aporte a){get(codigo);db.update("INSERT INTO aporte(codigo,monto,fecha_deposito) VALUES(?,?,?)",codigo,a.monto(),a.fechaDeposito());return Map.of("ok",true,"mensaje","Aporte declarado, pendiente de verificación manual");}
  @GetMapping("/admin/invitados") public List<Map<String,Object>> invitados(@RequestHeader(value="X-Admin-Key",required=false) String key){admin(key);return db.queryForList("SELECT codigo,nombre,cupos,estado,mensaje,ingreso_cupos FROM invitado ORDER BY nombre");}
  @PatchMapping("/admin/invitados/{codigo}/cupos") public Map<String,Object> cupos(@RequestHeader(value="X-Admin-Key",required=false) String key,@PathVariable String codigo,@Valid @RequestBody Cupos c){admin(key);get(codigo);int n=db.update("UPDATE invitado SET cupos=?,actualizado_en=now() WHERE codigo=? AND ingreso_cupos<=?",c.cupos(),codigo,c.cupos());if(n==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Los cupos no pueden ser menores que los ingresos registrados");return Map.of("ok",true);}
  @GetMapping("/pases/{token}/verificar") public Map<String,Object> verificar(@RequestHeader(value="X-Admin-Key",required=false) String key,@PathVariable UUID token){admin(key);var rows=db.queryForList("SELECT codigo,nombre,cupos,estado,ingreso_cupos FROM invitado WHERE pase_token=?",token);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Pase inexistente");return rows.get(0);}
  @PostMapping("/admin/pases/{token}/ingresos") @Transactional public Map<String,Object> ingresar(@RequestHeader(value="X-Admin-Key",required=false) String key,@PathVariable UUID token,@Valid @RequestBody Entrada e){admin(key);var rows=db.queryForList("SELECT codigo,cupos,ingreso_cupos,estado FROM invitado WHERE pase_token=? FOR UPDATE",token);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Pase inexistente");var r=rows.get(0);int cupos=((Number)r.get("cupos")).intValue(),usados=((Number)r.get("ingreso_cupos")).intValue();if(cupos==0||!"SI".equals(r.get("estado")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Invitación no habilitada o no confirmada");if(usados+e.cantidad()>cupos)throw new ResponseStatusException(HttpStatus.CONFLICT,"Excede cupos disponibles");String codigo=(String)r.get("codigo");db.update("UPDATE invitado SET ingreso_cupos=ingreso_cupos+?,actualizado_en=now() WHERE codigo=?",e.cantidad(),codigo);db.update("INSERT INTO ingreso(codigo,cantidad) VALUES(?,?)",codigo,e.cantidad());return Map.of("ok",true,"codigo",codigo,"ingresados",usados+e.cantidad(),"cupos",cupos);}
  @GetMapping("/admin/aportes") public List<Map<String,Object>> aportes(@RequestHeader(value="X-Admin-Key",required=false) String key){admin(key);return db.queryForList("SELECT a.id,a.codigo,i.nombre,a.monto,a.fecha_deposito,a.estado,a.creado_en FROM aporte a JOIN invitado i ON i.codigo=a.codigo ORDER BY a.creado_en DESC");}
  @PatchMapping("/admin/aportes/{id}") public Map<String,Object> verificarAporte(@RequestHeader(value="X-Admin-Key",required=false) String key,@PathVariable long id,@Valid @RequestBody EstadoAporte a){admin(key);int n=db.update("UPDATE aporte SET estado=? WHERE id=?",a.estado(),id);if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);return Map.of("ok",true);}
  @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public Map<String,String> invalid(){return Map.of("error","Datos inválidos");}
}
