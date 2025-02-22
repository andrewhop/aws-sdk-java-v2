package software.amazon.awssdk.services.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.assertj.core.data.Offset;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.signer.AwsS3V4Signer;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.core.signer.NoOpSigner;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.RequestPayer;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@RunWith(MockitoJUnitRunner.class)
public class S3PresignerTest {
    private static final URI FAKE_URL;

    private S3Presigner presigner;

    static {
        FAKE_URL = URI.create("https://localhost");
    }

    @Before
    public void setUp() {
        this.presigner = presignerBuilder().build();
    }

    @After
    public void tearDown() {
        this.presigner.close();
    }

    private S3Presigner.Builder presignerBuilder() {
        return S3Presigner.builder()
                          .region(Region.US_WEST_2)
                          .credentialsProvider(() -> AwsBasicCredentials.create("x", "x"));
    }

    private S3Presigner generateMaximal() {
        return S3Presigner.builder()
                          .credentialsProvider(() -> AwsBasicCredentials.create("x", "x"))
                          .region(Region.US_EAST_1)
                          .endpointOverride(FAKE_URL)
                          .build();
    }

    private S3Presigner generateMinimal() {
        return S3Presigner.builder()
                          .credentialsProvider(() -> AwsBasicCredentials.create("x", "x"))
                          .region(Region.US_EAST_1)
                          .build();
    }

    @Test
    public void build_allProperties() {
        generateMaximal();
    }

    @Test
    public void build_minimalProperties() {
        generateMinimal();
    }

    @Test
    public void basicGetObjectSignatureIsUrlCompatible() {
        PresignedGetObjectRequest presigned =
            presigner.presignGetObject(r -> r.signatureDuration(Duration.ofMinutes(5))
                                             .getObjectRequest(go -> go.bucket("foo34343434")
                                                                       .key("bar")
                                                                       .responseContentType("text/plain")));
        assertThat(presigned.isBrowserExecutable()).isTrue();
        assertThat(presigned.signedHeaders().keySet()).containsExactly("host");
        assertThat(presigned.signedPayload()).isEmpty();
    }

    @Test
    public void requesterPaysIsNotUrlCompatible() {
        PresignedGetObjectRequest presigned =
            presigner.presignGetObject(r -> r.signatureDuration(Duration.ofMinutes(5))
                                             .getObjectRequest(go -> go.bucket("foo34343434")
                                                                       .key("bar")
                                                                       .requestPayer(RequestPayer.REQUESTER)));
        assertThat(presigned.isBrowserExecutable()).isFalse();
        assertThat(presigned.signedHeaders().keySet()).containsExactlyInAnyOrder("host", "x-amz-request-payer");
        assertThat(presigned.signedPayload()).isEmpty();
    }

    @Test
    public void endpointOverrideIsIncludedInPresignedUrl() {
        S3Presigner presigner = presignerBuilder().endpointOverride(URI.create("http://foo.com")).build();
        PresignedGetObjectRequest presigned =
            presigner.presignGetObject(r -> r.signatureDuration(Duration.ofMinutes(5))
                                             .getObjectRequest(go -> go.bucket("foo34343434")
                                                                       .key("bar")));

        assertThat(presigned.url().toString()).startsWith("http://foo.com/foo34343434/bar?");
        assertThat(presigned.isBrowserExecutable()).isTrue();
        assertThat(presigned.signedHeaders().get("host")).containsExactly("foo.com");
        assertThat(presigned.signedPayload()).isEmpty();
    }

    @Test
    public void bodyAddedByInterceptorIsIncluded() {
        S3Presigner presigner = presignerBuilder().endpointOverride(URI.create("http://foo.com")).build();
        PresignedGetObjectRequest presigned =
            presigner.presignGetObject(r -> r.signatureDuration(Duration.ofMinutes(5))
                                             .getObjectRequest(go -> go.bucket("foo34343434")
                                                                       .key("bar")));

        assertThat(presigned.url().toString()).startsWith("http://foo.com/foo34343434/bar?");
        assertThat(presigned.isBrowserExecutable()).isTrue();
        assertThat(presigned.signedHeaders().get("host")).containsExactly("foo.com");
        assertThat(presigned.signedPayload()).isEmpty();
    }

    @Test
    public void credentialsCanBeOverriddenAtTheRequestLevel() {
        AwsCredentials clientCredentials = AwsBasicCredentials.create("a", "a");
        AwsCredentials requestCredentials = AwsBasicCredentials.create("b", "b");

        S3Presigner presigner = presignerBuilder().credentialsProvider(() -> clientCredentials).build();


        AwsRequestOverrideConfiguration overrideConfiguration =
            AwsRequestOverrideConfiguration.builder()
                                           .credentialsProvider(() -> requestCredentials)
                                           .build();

        PresignedGetObjectRequest presignedWithClientCredentials =
            presigner.presignGetObject(r -> r.signatureDuration(Duration.ofMinutes(5))
                                             .getObjectRequest(go -> go.bucket("foo34343434")
                                                                       .key("bar")));

        PresignedGetObjectRequest presignedWithRequestCredentials =
            presigner.presignGetObject(r -> r.signatureDuration(Duration.ofMinutes(5))
                                             .getObjectRequest(go -> go.bucket("foo34343434")
                                                                       .key("bar")
                                                                       .overrideConfiguration(overrideConfiguration)));

        System.out.println(presignedWithClientCredentials.url());

        assertThat(presignedWithClientCredentials.httpRequest().rawQueryParameters().get("X-Amz-Credential").get(0))
            .startsWith("a");
        assertThat(presignedWithRequestCredentials.httpRequest().rawQueryParameters().get("X-Amz-Credential").get(0))
            .startsWith("b");
    }

    @Test
    public void additionalHeadersAndQueryStringsCanBeAdded() {
        AwsRequestOverrideConfiguration override =
            AwsRequestOverrideConfiguration.builder()
                                           .putHeader("X-Amz-AdditionalHeader", "foo1")
                                           .putRawQueryParameter("additionalQueryParam", "foo2")
                                           .build();

        PresignedGetObjectRequest presigned =
            presigner.presignGetObject(r -> r.signatureDuration(Duration.ofMinutes(5))
                                             .getObjectRequest(go -> go.bucket("foo34343434")
                                                                       .key("bar")
                                                                       .overrideConfiguration(override)));

        assertThat(presigned.isBrowserExecutable()).isFalse();
        assertThat(presigned.signedHeaders()).containsOnlyKeys("host", "x-amz-additionalheader");
        assertThat(presigned.signedHeaders().get("x-amz-additionalheader")).containsExactly("foo1");
        assertThat(presigned.httpRequest().headers()).containsKeys("x-amz-additionalheader");
        assertThat(presigned.httpRequest().rawQueryParameters().get("additionalQueryParam").get(0)).isEqualTo("foo2");
    }

    @Test
    public void nonSigV4SignersRaisesException() {
        AwsRequestOverrideConfiguration override =
            AwsRequestOverrideConfiguration.builder()
                                           .signer(new NoOpSigner())
                                           .build();

        assertThatThrownBy(() -> presigner.presignGetObject(r -> r.signatureDuration(Duration.ofMinutes(5))
                                                                  .getObjectRequest(go -> go.bucket("foo34343434")
                                                                                            .key("bar")
                                                                                            .overrideConfiguration(override))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("NoOpSigner");
    }

    @Test
    public void sigv4PresignerHonorsSignatureDuration() {
        AwsRequestOverrideConfiguration override =
            AwsRequestOverrideConfiguration.builder()
                                           .signer(AwsS3V4Signer.create())
                                           .build();

        PresignedGetObjectRequest presigned =
            presigner.presignGetObject(r -> r.signatureDuration(Duration.ofSeconds(1234))
                                             .getObjectRequest(gor -> gor.bucket("a")
                                                                         .key("b")
                                                                         .overrideConfiguration(override)));

        assertThat(presigned.httpRequest().rawQueryParameters().get("X-Amz-Expires").get(0)).satisfies(expires -> {
            assertThat(expires).containsOnlyDigits();
            assertThat(Integer.parseInt(expires)).isCloseTo(1234, Offset.offset(2));
        });
    }
}