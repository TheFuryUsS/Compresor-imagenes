import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

public class RawImageLoader {

    public static void main(String[] args) {
        if (args.length < 7) {
            System.out.println(
                    "Uso: java RawImageLoader <nombre_app> <nombre_imagen> <filas> <columnas> <num_componentes> <bytes_datatype> <num_pixeles>");
            return;
        }

        String nombreApp = args[0];
        String nombreImagen = args[1];
        int filas = Integer.parseInt(args[2]);
        int columnas = Integer.parseInt(args[3]);
        int numComponentes = Integer.parseInt(args[4]);
        int bytesDatatype = Integer.parseInt(args[5]);
        int numPixeles = Integer.parseInt(args[6]);

        System.out.println("Iniciando " + nombreApp);

        String archivoNombre = String.format("%s.%d_%d_%d_%d_0_%d_0_0_0.raw", nombreImagen, numComponentes, filas, columnas, bytesDatatype, numPixeles);

        String carpeta = "C:\\Users\\ivax1\\OneDrive\\Escritorio\\UAB\\4t\\TCI\\Projecte 1\\imatges\\imatges";
        File archivo = new File(carpeta, archivoNombre);

        if (!archivo.exists()) {
            System.out.println("El archivo no existe: " + archivo.getAbsolutePath());
            return;
        }

        try {
            // Cargar imagen original
            int[][][] matriz = cargarImagenRaw(archivo, filas, columnas, numComponentes, bytesDatatype);
            if (matriz != null) {
                imprimirValoresLimites(matriz);

                double entropiaOriginal = calcularEntropia(matriz, numComponentes);
                System.out.printf("Entropia original de la imagen: %.4f bits\n", entropiaOriginal);

                // Verificar si las imágenes cuantizadas ya están guardadas
                String carpetaCuantizadas = "C:\\Users\\ivax1\\OneDrive\\Escritorio\\UAB\\4t\\TCI\\Projecte 1\\imatges\\imatgesCuantitzades";

                // Cuantización con Qstep de 1 y luego de 5 en 5 hasta 50
                for (int nivelesCuantizacion = 1; nivelesCuantizacion <= 50; nivelesCuantizacion = (nivelesCuantizacion == 1) ? 5 : nivelesCuantizacion + 5) {
                    int[][][] imagenCuantizada;
                    int[][][] imagenDescuantizada;

                    String archivoCuantizadoNombre = String.format("%s\\imatgeGuardada_%d.raw", carpetaCuantizadas, nivelesCuantizacion);
                    File archivoCuantizado = new File(archivoCuantizadoNombre);

                    if (!archivoCuantizado.exists()) {
                        String carpetaSalida = carpetaCuantizadas;
                        new File(carpetaSalida).mkdirs(); // Crear la carpeta si no existe
                        // Cuantizar y descuantizar la imagen
                        imagenCuantizada = cuantizarImagen(matriz, nivelesCuantizacion);
                        imagenDescuantizada = descuantizarImagen(imagenCuantizada, nivelesCuantizacion);

                        // Guardar la imagen descuantizada en un archivo RAW
                        String archivoSalida = String.format("%s\\imatgeGuardada_%d.raw", carpetaSalida, nivelesCuantizacion);
                        guardarImagenRaw(imagenDescuantizada, archivoSalida, filas, columnas, numComponentes, bytesDatatype);

                    } else {
                        String archivoDescuantizadoNombre = String.format("%s\\imatgeGuardada_%d.raw", carpetaCuantizadas, nivelesCuantizacion);
                        imagenDescuantizada = cargarImagenRaw(new File(archivoDescuantizadoNombre), filas, columnas, numComponentes, bytesDatatype);
                    }

                    if (imagenDescuantizada != null) {
                        double pae = calcularPAE(matriz, imagenDescuantizada, numComponentes);
                        double mse = calcularMSE(matriz, imagenDescuantizada, numComponentes);
                        double psnr = calcularPSNR(mse, bytesDatatype);
                        double entropiaCuantizada = calcularEntropia(imagenDescuantizada, numComponentes);

                        System.out.printf("Imagen cuantizada con paso %d:\n", nivelesCuantizacion);

                        System.out.printf("Entropia: %.4f bits\n", entropiaCuantizada);
                        System.out.printf("PAE: %.4f\n", pae);
                        System.out.printf("MSE: %.4f\n", mse);
                        System.out.printf("PSNR: %s\n", psnr == -1 ? "Infinito\n" : String.format("%.4f dB\n", psnr));

                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Error al leer la imagen: " + e.getMessage());
        }
    }

    private static int[][][] cargarImagenRaw(File archivo, int filas, int columnas, int numComponentes, int bytesDatatype) throws IOException {
        int pixelSize = bytesDatatype;
        int imageSize = filas * columnas * numComponentes * pixelSize;
        byte[] rawData = new byte[imageSize];

        try (FileInputStream fis = new FileInputStream(archivo)) {
            int bytesRead = fis.read(rawData);
            if (bytesRead != imageSize) {
                System.out.println("Tamaño incorrecto de datos en " + archivo.getName());
                return null;
            }
        }

        int[][][] matriz = new int[filas][columnas][numComponentes];

        for (int y = 0; y < filas; y++) {
            for (int x = 0; x < columnas; x++) {
                for (int c = 0; c < numComponentes; c++) {
                    int pixelIndex = (y * columnas + x) * numComponentes * pixelSize + c * pixelSize;
                    matriz[y][x][c] = leerValor(rawData, pixelIndex, bytesDatatype);
                }
            }
        }

        return matriz;
    }

    private static int leerValor(byte[] rawData, int index, int bytesDatatype) {
        if (bytesDatatype == 1) {
            return rawData[index] & 0xFF;
        } else if (bytesDatatype == 2) {
            ByteBuffer buffer = ByteBuffer.wrap(rawData, index, 2).order(ByteOrder.BIG_ENDIAN);
            return buffer.getShort() & 0xFFFF;
        } else if (bytesDatatype == 3) {
            ByteBuffer buffer = ByteBuffer.wrap(rawData, index, 2).order(ByteOrder.BIG_ENDIAN);
            return buffer.getShort();
        }
        return 0;
    }

    private static void imprimirValoresLimites(int[][][] matriz) {
        if (matriz.length == 0 || matriz[0].length == 0) {
            System.out.println("La matriz está vacia.");
            return;
        }

        System.out.println("Primeros 10 valores de la primera fila:");
        for (int j = 0; j < Math.min(10, matriz[0].length); j++) {
            System.out.print("[");
            for (int c = 0; c < matriz[0][j].length; c++) {
                int value = matriz[0][j][c];
                System.out.printf("%d", value);
                if (c < matriz[0][j].length - 1)
                    System.out.print(", ");
            }
            System.out.print("] ");
        }

        System.out.println("\nUltimos 10 valores de la ultima fila:");
         int lastRow = matriz.length - 1;
        for (int j = Math.max(0, matriz[lastRow].length - 10); j < matriz[lastRow].length; j++) {
            System.out.print("[");
            for (int c = 0; c < matriz[lastRow][j].length; c++) {
                int value = matriz[lastRow][j][c];
                System.out.printf("%d", value);
                if (c < matriz[lastRow][j].length - 1)
                    System.out.print(", ");
            }
            System.out.print("] ");
        }
        System.out.println();
    }

    private static double calcularEntropia(int[][][] matriz, int numComponentes) {
        Map<Integer, Integer> frecuencia = new HashMap<>();
        int totalPixeles = matriz.length * matriz[0].length * numComponentes;

        for (int[][] fila : matriz) {
            for (int[] pixel : fila) {
                for (int componente : pixel) {
                    frecuencia.put(componente, frecuencia.getOrDefault(componente, 0) + 1);
                }
            }
        }

        double entropia = 0.0;
        for (int count : frecuencia.values()) {
            double probabilidad = (double) count / totalPixeles;
            entropia += probabilidad * (Math.log(probabilidad) / Math.log(2));
        }

        return -entropia;
    }

    private static int[][][] cuantizarImagen(int[][][] matriz, int factorCuantizacion) {
        int[][][] imagenCuantizada = new int[matriz.length][matriz[0].length][matriz[0][0].length];

        for (int i = 0; i < matriz.length; i++) {
            for (int j = 0; j < matriz[0].length; j++) {
                for (int k = 0; k < matriz[0][0].length; k++) {
                    int valorOriginal = matriz[i][j][k];
                    int valorAbsoluto = Math.abs(valorOriginal);
                    int valorCuantizado = valorAbsoluto / factorCuantizacion;

                    imagenCuantizada[i][j][k] = valorOriginal < 0 ? -valorCuantizado : valorCuantizado;
                }
            }
        }

        return imagenCuantizada;
    }

    private static int[][][] descuantizarImagen(int[][][] imagenCuantizada, int factorCuantizacion) {
        int[][][] imagenDescuantizada = new int[imagenCuantizada.length][imagenCuantizada[0].length][imagenCuantizada[0][0].length];
    
        for (int i = 0; i < imagenCuantizada.length; i++) {
            for (int j = 0; j < imagenCuantizada[0].length; j++) {
                for (int k = 0; k < imagenCuantizada[0][0].length; k++) {
                    imagenDescuantizada[i][j][k] = imagenCuantizada[i][j][k] * factorCuantizacion;
                }
            }
        }
    
        return imagenDescuantizada;
    }
    

    private static void guardarImagenRaw(int[][][] matriz, String archivoSalida, int filas, int columnas,
            int numComponentes, int bytesDatatype) throws IOException {
        // Crear el archivo de salida
        File archivo = new File(archivoSalida);
        archivo.getParentFile().mkdirs(); // Crear la carpeta si no existe
        try (FileOutputStream fos = new FileOutputStream(archivo)) {
            for (int i = 0; i < filas; i++) {
                for (int j = 0; j < columnas; j++) {
                    for (int c = 0; c < numComponentes; c++) {
                        int valor = matriz[i][j][c];
                        if (bytesDatatype == 1) {
                            fos.write(valor & 0xFF);
                        } else if (bytesDatatype == 2) {
                            fos.write((valor >> 8) & 0xFF); // Byte alto
                            fos.write(valor & 0xFF); // Byte bajo
                        } else if (bytesDatatype == 3) {
                            // Suponiendo que es un valor de 16 bits, escribe 2 bytes
                            fos.write((valor >> 8) & 0xFF); // Byte alto
                            fos.write(valor & 0xFF); // Byte bajo
                        }
                    }
                }
            }
        }
        System.out.println("Imagen descuantizada guardada en: " + archivoSalida);
    }

    private static double calcularPAE(int[][][] original, int[][][] cuantizada, int numComponentes) {
        double maxError = 0.0; // Inicializamos el máximo error
    
        for (int y = 0; y < original.length; y++) {
            for (int x = 0; x < original[0].length; x++) {
                for (int c = 0; c < numComponentes; c++) {
                    // Calculamos la diferencia absoluta entre el pixel original y el cuantizado
                    double error = Math.abs(original[y][x][c] - cuantizada[y][x][c]);
                    // Actualizamos el máximo error si encontramos uno mayor
                    if (error > maxError) {
                        maxError = error;
                    }
                }
            }
        }
    
        return maxError; // Devolvemos el mayor error encontrado
    }

    private static double calcularMSE(int[][][] original, int[][][] cuantizada, int numComponentes) {
        double sumaErroresCuadrados = 0.0;
        int totalPixeles = original.length * original[0].length * numComponentes;

        for (int y = 0; y < original.length; y++) {
            for (int x = 0; x < original[0].length; x++) {
                for (int c = 0; c < numComponentes; c++) {
                    int error = original[y][x][c] - cuantizada[y][x][c];
                    sumaErroresCuadrados += error * error;
                }
            }
        }

        return sumaErroresCuadrados / totalPixeles; // Devuelve el MSE
    }

    private static double calcularPSNR(double mse, int bytesDatatype) {
        if (mse == 0) {
            return -1; // Si no hay error, PSNR es infinito
        }
        double maxPixelValue = Math.pow(2, bytesDatatype * 8) - 1; // Valor máximo del pixel según el número de bytes
        return 10 * Math.log10((maxPixelValue * maxPixelValue) / mse);
    }
}
