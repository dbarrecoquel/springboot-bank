package com.bank.infrastructure.notification;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;

import com.bank.common.exception.NotificationException;
import com.bank.domain.entity.Notification;
import org.thymeleaf.context.Context;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailAdapter {
	
	private final JavaMailSender mailSender;
	private final TemplateEngine templateEngine;
	
    @Value("${banking.mail.from:noreply@bank.com}")
    private String fromAddress;
 
    @Value("${banking.mail.from-name:Votre Banque}")
    private String fromName;
 
    @Value("${banking.mail.enabled:true}")
    private boolean enabled;
    
    @Async("notificationExecutor")
    public void send(Notification notification) {
    	
    	validateChannel(notification);
    	
    	if (!enabled) {
    		
            log.info("[EMAIL] Envoi désactivé (profil dev) — to={} subject={}",
                    notification.getRecipient(), notification.getSubject());

    		notification.markSent();
    	}
    	try {
    		
    		MimeMessage message = buildMimeMessage(notification, null);
    		mailSender.send(message);
    		notification.markSent();
    		
            log.info("[EMAIL] Envoyé — to={} subject={} notifId={}",
                    notification.getRecipient(),
                    notification.getSubject(),
                    notification.getId());

    	}
    	catch (MessagingException | UnsupportedEncodingException ex) {
    		notification.markFailed(ex.getMessage());
            log.error("[EMAIL] Échec envoi — to={} subject={} notifId={} error={}",
                    notification.getRecipient(), notification.getSubject(),
                    notification.getId(), ex.getMessage(), ex);
          throw new NotificationException("Échec envoi email à " + notification.getRecipient(), ex);

    	}
    	
    }
    
    @Async("notificationExecutor")
    public void sendWithTemplateVars(Notification notification, Map<String, Object> templateVars) {
    	
        validateChannel(notification);
        
        if (!enabled) {
            log.info("[EMAIL] Envoi désactivé — to={}", notification.getRecipient());
            notification.markSent();
            return;
        }
 
        try {
            MimeMessage message = buildMimeMessage(notification, templateVars);
            mailSender.send(message);
            notification.markSent();
 
            log.info("[EMAIL] Envoyé (template={}) — to={} notifId={}",
                     notification.getTemplateKey(),
                     notification.getRecipient(),
                     notification.getId());
 
        } catch (MessagingException | UnsupportedEncodingException ex) {
            notification.markFailed(ex.getMessage());
            log.error("[EMAIL] Échec envoi — to={} template={} error={}",
                      notification.getRecipient(), notification.getTemplateKey(),
                      ex.getMessage(), ex);
            throw new NotificationException("Échec envoi email", ex);
        }

    }
    
    public void sendToCompliance(String subject, String body) {
    	
    	if (!enabled)
    		return;
    	
    	try {
    		
    		MimeMessage message = mailSender.createMimeMessage();
    		MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
    		
    		helper.setFrom(fromAddress, fromName);
    		helper.setTo("${banking.mail.compliance:compliance@bank.com}");
    		helper.setSubject("[COMPLIANCE] " + subject);
    		helper.setText(body, false);
    		mailSender.send(message);
    		
    		log.info("[EMAIL] Alerte compliance envoyée — subject={}", subject);
    	}
    	catch (Exception ex) {
            log.error("[EMAIL] Échec envoi compliance — subject={} error={}",
                      subject, ex.getMessage(), ex);
        }

    }
    
    private MimeMessage buildMimeMessage(Notification notification,Map<String, Object> extraVars)
            throws MessagingException, UnsupportedEncodingException {
    
    	MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message,MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,StandardCharsets.UTF_8.name());
     
        helper.setFrom(fromAddress, fromName);
        helper.setTo(notification.getRecipient());
        helper.setSubject(notification.getSubject() != null
            ? notification.getSubject() : "(sans objet)");
 
        // Rendu Thymeleaf si un template est spécifié
        if (notification.getTemplateKey() != null && !notification.getTemplateKey().isBlank()) {
            String htmlContent = renderTemplate(notification, extraVars);
            helper.setText(notification.getBody(), htmlContent); // (text, html)
        } else {
            // Corps brut — détection simple HTML vs texte
            boolean isHtml = notification.getBody() != null
                && notification.getBody().trim().startsWith("<");
            helper.setText(notification.getBody(), isHtml);
        }
 
        return message; 
        
    }
    
    private String renderTemplate(Notification notification, Map<String, Object> extraVars) {
    	
    	Context ctx = new Context(Locale.FRENCH);
    	
        ctx.setVariable("body", notification.getBody());
        ctx.setVariable("subject", notification.getSubject());
        ctx.setVariable("notifId", notification.getId());
        ctx.setVariable("sourceType", notification.getSourceType());
        ctx.setVariable("sourceId", notification.getSourceId());
        
        if (extraVars != null)
        	extraVars.forEach(ctx::setVariable);

    	String templatePath = "email/" + notification.getTemplateKey();
    	
        try {
            return templateEngine.process(templatePath, ctx);
        } catch (Exception ex) {
            log.warn("[EMAIL] Template '{}' introuvable — utilisation du corps brut. error={}",
                     templatePath, ex.getMessage());
            // Fallback : corps brut si le template est absent
            return notification.getBody();
        }

    }

    private void validateChannel(Notification notification) {
        if (notification.getChannel() != Notification.Channel.EMAIL) {
            throw new IllegalArgumentException(
                "EmailAdapter ne gère que le canal EMAIL — reçu : "
                    + notification.getChannel());
        }
        if (notification.getRecipient() == null
                || notification.getRecipient().isBlank()) {
            throw new IllegalArgumentException(
                "Adresse email destinataire manquante — notifId=" + notification.getId());
        }
    }

}
