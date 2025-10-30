import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class RandomDogAutoPublisher {

    // ⚙️ Configuración básica en el propio código
    private static final String OUTPUT_PATH = "docs/index.html";  // o "index.html" si publicas desde raíz
    private static final String REMOTE = "origin";
    private static final String BRANCH = "main";                  // o "gh-pages"
    private static final int INTERVAL_SECONDS = 10;               // cada 10 segundos

    // --- Método principal ---
    public static void main(String[] args) {
        System.out.printf("🚀 Publicador automático cada %d s → %s (%s/%s)%n",
                INTERVAL_SECONDS, OUTPUT_PATH, REMOTE, BRANCH);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        // Cierra correctamente al terminar
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Finalizando...");
            scheduler.shutdown();
        }));

        Runnable tarea = () -> {
            try {
                // 1️⃣ Obtener imagen aleatoria
                String json = runAndCapture("curl", "-s", "https://dog.ceo/api/breeds/image/random");
                String imageUrl = extraerURL(json);
                if (imageUrl == null) {
                    System.err.println("⚠️ No se pudo extraer la URL del perro.");
                    return;
                }

                System.out.println("🐶 " + imageUrl);

                // 2️⃣ Generar HTML
                generarHTML(OUTPUT_PATH, imageUrl);

                // 3️⃣ git add .
                run("git", "add", ".");

                // 4️⃣ git commit -m "..."
                String mensaje = "feat(dog): auto-update " + timestamp();
                int code = run("git", "commit", "-m", mensaje);
                if (code != 0) {
                    System.out.println("ℹ️ Sin cambios que commitear (probablemente).");
                }

                // 5️⃣ git push origin main
                run("git", "push", REMOTE, BRANCH);
                System.out.println("✅ Despliegue enviado a GitHub Pages\n");

            } catch (Exception e) {
                System.err.println("❌ Error en el ciclo: " + e.getMessage());
            }
        };

        // Ejecuta ahora y luego cada INTERVAL_SECONDS
        scheduler.scheduleAtFixedRate(tarea, 0, INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    // --- Funciones auxiliares ---
    private static String runAndCapture(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder salida = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = br.readLine()) != null) salida.append(linea);
        }
        p.waitFor();
        return salida.toString();
    }

    private static int run(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = br.readLine()) != null) System.out.println(linea);
        }
        return p.waitFor();
    }

    private static void generarHTML(String ruta, String imageUrl) throws IOException {
        String html = """
                <!doctype html>
                <html lang="es"><head>
                <meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Perrito aleatorio 🐶</title>
                <style>
                body{font-family:sans-serif;display:grid;place-items:center;padding:2rem}
                img{max-width:min(90vw,700px);border-radius:12px;box-shadow:0 5px 25px rgba(0,0,0,.2)}
                .time{margin-top:1rem;opacity:.6}
                </style></head><body>
                <h1>Perrito aleatorio 🐶</h1>
                <img src="%s" alt="Perrito">
                <div class="time">Actualizado: %s</div>
                </body></html>
                """.formatted(imageUrl, timestamp());

        Path salida = Paths.get(ruta);
        if (salida.getParent() != null) Files.createDirectories(salida.getParent());
        Files.writeString(salida, html, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        System.out.println("📝 Página actualizada en " + salida.toAbsolutePath());
    }

    private static String extraerURL(String json) {
        int inicio = json.indexOf("\"message\":\"");
        if (inicio < 0) return null;
        inicio += 11;
        int fin = json.indexOf("\"", inicio);
        if (fin < 0) return null;
        return json.substring(inicio, fin).replace("\\/", "/");
    }

    private static String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
