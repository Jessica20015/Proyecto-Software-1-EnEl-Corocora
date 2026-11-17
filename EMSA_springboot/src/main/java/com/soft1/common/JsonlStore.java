package com.soft1.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Almacén de archivos de texto del proyecto ("data stores" simulados del modelo):
 * expedientes en formato JSON-Lines y el consecutivo de radicados.
 */
@Component
public class JsonlStore {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path carpeta;

    public JsonlStore(@Value("${emsa.expediente-dir:./expediente}") String carpeta) {
        this.carpeta = Path.of(carpeta);
    }

    public Path ruta(String archivo) {
        return carpeta.resolve(archivo);
    }

    /** Agrega una línea JSON al archivo. */
    public synchronized void agregar(String archivo, Map<String, Object> registro) {
        try {
            Files.createDirectories(carpeta);
            String linea = mapper.writeValueAsString(registro) + "\n";
            Files.writeString(ruta(archivo), linea, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el registro del expediente", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Lee todas las líneas JSON del archivo (lista vacía si no existe). */
    public synchronized List<Map<String, Object>> leer(String archivo) {
        Path p = ruta(archivo);
        List<Map<String, Object>> registros = new ArrayList<>();
        if (!Files.exists(p)) {
            return registros;
        }
        try {
            for (String linea : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                if (!linea.isBlank()) {
                    registros.add(mapper.readValue(linea, new TypeReference<Map<String, Object>>() {
                    }));
                }
            }
            return registros;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Incrementa y devuelve el consecutivo guardado en el archivo (se asigna en serie: no repite radicados). */
    public synchronized int siguienteConsecutivo(String archivo) {
        try {
            Files.createDirectories(carpeta);
            Path p = ruta(archivo);
            int actual = 0;
            if (Files.exists(p)) {
                try {
                    actual = Integer.parseInt(Files.readString(p, StandardCharsets.UTF_8).trim());
                } catch (NumberFormatException ignorado) {
                    actual = 0;
                }
            }
            int siguiente = actual + 1;
            Files.writeString(p, String.valueOf(siguiente), StandardCharsets.UTF_8);
            return siguiente;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
