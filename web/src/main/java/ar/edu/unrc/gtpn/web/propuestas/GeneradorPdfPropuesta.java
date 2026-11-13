package ar.edu.unrc.gtpn.web.propuestas;

import java.io.ByteArrayOutputStream;
import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

@Service
public class GeneradorPdfPropuesta {

    /**
     * Genera un documento PDF en un arreglo de bytes a partir de los datos de la propuesta.
     */
    public byte[] generarPdf(String nombreCurso, String razonSocialEmpresa, Propuesta propuesta) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font tituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font subtituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font textoFont = FontFactory.getFont(FontFactory.HELVETICA, 12);

            document.add(new Paragraph("Propuesta de Capacitación - Versión " + propuesta.version(), tituloFont));
            document.add(new Paragraph("Empresa: " + razonSocialEmpresa, subtituloFont));
            document.add(new Paragraph("Curso: " + nombreCurso, subtituloFont));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Programa:", subtituloFont));
            document.add(new Paragraph(propuesta.programa(), textoFont));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Presupuesto: $" + propuesta.presupuesto(), textoFont));
            document.add(new Paragraph("Fechas Posibles: " + propuesta.fechasPosibles(), textoFont));

            if (propuesta.observacionesEmpresa() != null && !propuesta.observacionesEmpresa().isBlank()) {
                document.add(new Paragraph(" "));
                document.add(new Paragraph("Observaciones recibidas de la empresa:", subtituloFont));
                document.add(new Paragraph(propuesta.observacionesEmpresa(), textoFont));
            }

            document.close();
        } catch (Exception e) {
            throw new RuntimeException("Error al generar el documento PDF", e);
        }

        return out.toByteArray();
    }
}
