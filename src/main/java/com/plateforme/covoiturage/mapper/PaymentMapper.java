package com.plateforme.covoiturage.mapper;
import com.plateforme.covoiturage.entity.*;
import com.plateforme.covoiturage.dto.response.*;
import org.springframework.stereotype.Component;
@Component public class PaymentMapper {
 public PaymentResponse history(com.plateforme.covoiturage.repository.PaymentHistoryProjection p){
  return new PaymentResponse(p.getPaiementId(),p.getNature(),p.getRessourceId(),p.getMontant(),p.getMode(),p.getValideLe(),
   p.getRembourseLe()==null?java.math.BigDecimal.ZERO:p.getMontant(),p.getRembourseLe());
 }
 public PaymentResponse place(PaiementPlace p){
  return new PaymentResponse(p.getId(),"PLACE",p.getReservation().getId(),p.getMontant(),p.getMode(),p.getValideLe(),
   p.getRembourseLe()==null?java.math.BigDecimal.ZERO:p.getMontant(),p.getRembourseLe());
 }
 public PaymentResponse debt(PaiementDette p){
  return new PaymentResponse(p.getId(),"DETTE",p.getDette().getId(),p.getMontant(),p.getMode(),p.getValideLe(),java.math.BigDecimal.ZERO,null);
 }
 public DetteResponse dette(DetteTrajet d){return new DetteResponse(d.getId(),d.getTrajet().getId(),d.getMontant(),d.getRegle(),d.getStatut());}
}
