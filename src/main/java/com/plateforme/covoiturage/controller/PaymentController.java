package com.plateforme.covoiturage.controller;
import com.plateforme.covoiturage.dto.request.*;
import com.plateforme.covoiturage.dto.response.*;
import com.plateforme.covoiturage.service.PaymentService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api") @RequiredArgsConstructor
@SecurityRequirement(name="bearerJwt")
@Tag(name="Paiements et dettes",description="Validation SIMULATION en dev uniquement, aucune integration bancaire reelle.")
public class PaymentController {
 private final PaymentService service;
 @GetMapping("/paiements/me")
 @Operation(summary="Consulter son historique des paiements validés",description="Places et règlements de dette séparés par nature ; propriétaire filtré avant pagination et total. Ordre date décroissante, nature puis identifiant décroissant. Aucun remboursement implicite.")
 public org.springframework.http.ResponseEntity<ReferencePageResponse<PaymentResponse>> history(
  @Parameter(hidden=true) Authentication actor,@Valid @ModelAttribute @ParameterObject ReferencePageRequest page){
  return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
   .body(service.history(actor,page));
 }
 @PostMapping("/reservations/{id}/paiements")
 @Operation(summary="Payer sa place de 20 EUR en simulation",description="Cle Idempotency-Key UUID ; confirmation atomique, aucun succes client accepte comme preuve.")
 public PaymentResponse seat(@Parameter(hidden=true) Authentication actor,@Positive @PathVariable long id,
  @RequestHeader("Idempotency-Key") UUID key){return service.paySeat(actor,id,key);}
 @GetMapping("/dettes/me") @Operation(summary="Consulter uniquement ses dettes, sans expiration")
 public ReferencePageResponse<DetteResponse> mine(@Parameter(hidden=true) Authentication actor,
  @Valid @ModelAttribute @ParameterObject ReferencePageRequest page){return service.mine(actor,page);}
 @PostMapping("/dettes/{id}/paiements")
 @Operation(summary="Regler tout ou partie de sa dette en simulation",description="Separation stricte des paiements de places ; cle UUID idempotente, aucun effet sur activite compte/client.")
 public PaymentResponse debt(@Parameter(hidden=true) Authentication actor,@Positive @PathVariable long id,
  @RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody DebtPaymentRequest request){
  return service.payDebt(actor,id,key,request.montant());
 }
}
