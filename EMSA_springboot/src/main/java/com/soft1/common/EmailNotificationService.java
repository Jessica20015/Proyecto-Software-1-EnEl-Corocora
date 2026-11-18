package com.soft1.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService {

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;

    public EmailNotificationService(JavaMailSender mailSender,
                                    @Value("${emsa.notificaciones.email.enabled:false}") boolean enabled,
                                    @Value("${emsa.notificaciones.email.from:}") String from) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
    }

    public boolean enviarConstancia(String destinatario, String radicado, String vencimiento) {
        if (!enabled) return false;
        if (destinatario == null || destinatario.isBlank()) {
            throw new IllegalArgumentException("La notificación electrónica requiere correoNotificacion.");
        }
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("Configure ENEL_MAIL_FROM para activar las notificaciones por correo.");
        }
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(from);
        mensaje.setTo(destinatario);
        mensaje.setSubject("ENEL: constancia de radicación " + radicado);
        mensaje.setText("Su solicitud fue radicada con el número " + radicado
                + ". Fecha de vencimiento del término: " + vencimiento + ".");
        mailSender.send(mensaje);
        return true;
    }
}
