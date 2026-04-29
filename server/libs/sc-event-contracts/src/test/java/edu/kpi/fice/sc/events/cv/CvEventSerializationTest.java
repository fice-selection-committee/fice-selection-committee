package edu.kpi.fice.sc.events.cv;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kpi.fice.sc.events.constants.EventConstants;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Wire-format contract for the three CV events. The same JSON shape is consumed by the Python
 * cv-service polyrepo (Pydantic mirrors); see {@code tests/events/samples/} there.
 */
class CvEventSerializationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void requestedEvent_roundTripsThroughJackson() throws Exception {
    var original =
        new CvDocumentRequestedEvent(
            42L, "documents/passports/abc123.png", "passport", "00-abc-01");

    String json = mapper.writeValueAsString(original);
    CvDocumentRequestedEvent decoded = mapper.readValue(json, CvDocumentRequestedEvent.class);

    assertThat(decoded).isEqualTo(original);
    assertThat(json)
        .contains("\"documentId\":42")
        .contains("\"s3Key\":\"documents/passports/abc123.png\"")
        .contains("\"documentType\":\"passport\"")
        .contains("\"traceId\":\"00-abc-01\"");
  }

  @Test
  void parsedEvent_roundTripsThroughJackson() throws Exception {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("surname", "Шевченко");
    fields.put("givenName", "Тарас");
    fields.put("ipn", "1234567890");

    var original = new CvDocumentParsedEvent(42L, fields, 0.94, "00-abc-01");

    String json = mapper.writeValueAsString(original);
    CvDocumentParsedEvent decoded = mapper.readValue(json, CvDocumentParsedEvent.class);

    assertThat(decoded).isEqualTo(original);
    assertThat(json).contains("\"confidence\":0.94").contains("\"fields\":{");
  }

  @Test
  void failedEvent_roundTripsThroughJackson() throws Exception {
    var original = new CvDocumentFailedEvent(42L, "preprocess.timeout", true, "00-abc-01");

    String json = mapper.writeValueAsString(original);
    CvDocumentFailedEvent decoded = mapper.readValue(json, CvDocumentFailedEvent.class);

    assertThat(decoded).isEqualTo(original);
    assertThat(json).contains("\"error\":\"preprocess.timeout\"").contains("\"retriable\":true");
  }

  @Test
  void parsedEvent_decodesCanonicalSampleJson() throws Exception {
    String canonical =
        "{\"documentId\":42,"
            + "\"fields\":{\"surname\":\"Шевченко\",\"givenName\":\"Тарас\"},"
            + "\"confidence\":0.94,"
            + "\"traceId\":\"00-abc-01\"}";

    CvDocumentParsedEvent decoded = mapper.readValue(canonical, CvDocumentParsedEvent.class);

    assertThat(decoded.documentId()).isEqualTo(42L);
    assertThat(decoded.fields()).containsEntry("surname", "Шевченко");
    assertThat(decoded.confidence()).isEqualTo(0.94);
    assertThat(decoded.traceId()).isEqualTo("00-abc-01");
  }

  @Test
  void eventConstants_exposeCvTopologyNames() {
    assertThat(EventConstants.CV_EVENTS_EXCHANGE).isEqualTo("cv.events");
    assertThat(EventConstants.CV_DLX).isEqualTo("cv.dlx");
    assertThat(EventConstants.CV_REQUEST_QUEUE).isEqualTo("cv.document.requested");
    assertThat(EventConstants.CV_RESULT_QUEUE).isEqualTo("cv.document.results");
    assertThat(EventConstants.CV_DLQ).isEqualTo("cv.dlq");
    assertThat(EventConstants.CV_REQUESTED_ROUTING_KEY).isEqualTo("cv.document.requested");
    assertThat(EventConstants.CV_PARSED_ROUTING_KEY).isEqualTo("cv.document.parsed");
    assertThat(EventConstants.CV_FAILED_ROUTING_KEY).isEqualTo("cv.document.failed");
  }
}
