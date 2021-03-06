package com.plivo.api;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.plivo.api.util.Utils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ProtocolException;
import java.net.Proxy;
import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;
import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.OkHttpClient.Builder;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class PlivoClient {

  private boolean testing = false;
  protected static String BASE_URL = "https://api.plivo.com/v1/";
  private static ObjectMapper objectMapper = new ObjectMapper();
  private static String version = "Unknown Version";

  public void setTesting(boolean testing) {
    this.testing = testing;
  }

  public boolean isTesting() {
    return testing;
  }

  static {
    try {
      InputStream inputStream = PlivoClient.class
        .getResource("version.txt")
        .openStream();

      version = new BufferedReader(new InputStreamReader(inputStream)).readLine();
    } catch (IOException ignored) {
      ignored.printStackTrace();
    }
    objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    objectMapper.disable(SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS);
    objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
    objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
    SimpleModule simpleModule = new SimpleModule();
    simpleModule.setDeserializerModifier(new BeanDeserializerModifier() {
      @Override
      public JsonDeserializer<?> modifyEnumDeserializer(DeserializationConfig config, JavaType type,
        BeanDescription beanDesc, JsonDeserializer<?> deserializer) {
        return new JsonDeserializer<Enum>() {
          @Override
          public Enum deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
            Class<? extends Enum> rawClass = (Class<Enum<?>>) type.getRawClass();
            return Enum.valueOf(rawClass, jp.getValueAsString().toUpperCase().replace("-", "_"));
          }
        };
      }
    });
    simpleModule.addSerializer(Enum.class, new StdSerializer<Enum>(Enum.class) {
      @Override
      public void serialize(Enum value, JsonGenerator gen, SerializerProvider provider)
        throws IOException {
        gen.writeString(value.name().toLowerCase().replace("_", "-"));
      }
    });
    objectMapper.registerModule(simpleModule);
  }

  private final Interceptor interceptor = new HttpLoggingInterceptor()
    .setLevel(Level.BODY);
  private final String authId;
  private final String authToken;
  private OkHttpClient httpClient;
  private Retrofit retrofit;
  private PlivoAPIService apiService = null;

  public PlivoClient(String authId, String authToken) {
    this(authId, authToken, new Builder());
  }

  /**
   * Constructs a new PlivoClient instance. To set a proxy, timeout etc, you can pass in an OkHttpClient.Builder, on which you can set
   * the timeout and proxy using:
   *
   * <pre><code>
   *   new OkHttpClient.Builder()
   *   .proxy(proxy)
   *   .connectTimeout(1, TimeUnit.MINUTES);
   * </code></pre>
   *
   * @param authId
   * @param authToken
   * @param httpClientBuilder
   */
  public PlivoClient(String authId, String authToken, OkHttpClient.Builder httpClientBuilder) {
    if (!(Utils.isAccountIdValid(authId) || Utils.isSubaccountIdValid(authId))) {
      throw new IllegalArgumentException("invalid account ID");
    }

    this.authId = authId;
    this.authToken = authToken;

     httpClient = httpClientBuilder
      .addNetworkInterceptor(interceptor)
      .addInterceptor(chain -> chain.proceed(
        chain.request()
          .newBuilder()
          .addHeader("Authorization", Credentials.basic(getAuthId(), getAuthToken()))
          .addHeader("User-Agent", String.format("%s/%s (Implementation: %s %s %s, Specification: %s %s %s)", "plivo-java", version,
            Runtime.class.getPackage().getImplementationVendor(),
            Runtime.class.getPackage().getImplementationTitle(),
            Runtime.class.getPackage().getImplementationVersion(),
            Runtime.class.getPackage().getSpecificationVendor(),
            Runtime.class.getPackage().getSpecificationTitle(),
            Runtime.class.getPackage().getSpecificationVersion()
          ))
          .build()
      ))
      .addNetworkInterceptor(chain -> {
        Response response;
        try {
          response = chain.proceed(chain.request());
        } catch (ProtocolException protocolException) {
          // We return bodies for HTTP 204!
          response = new Response.Builder()
            .request(chain.request())
            .code(204)
            .protocol(Protocol.HTTP_1_1)
            .body(ResponseBody.create(null, new byte[]{}))
            .build();
        }
        return response;
      }).build();

     retrofit = new Retrofit.Builder()
      .client(httpClient)
      .baseUrl(BASE_URL)
      .addConverterFactory(JacksonConverterFactory.create(objectMapper))
      .build();

    this.apiService = retrofit.create(PlivoAPIService.class);
  }

  public static ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  Retrofit getRetrofit() {
    return retrofit;
  }

  public PlivoAPIService getApiService() {
    return apiService;
  }

  void setApiService(PlivoAPIService apiService) {
    this.apiService = apiService;
  }

  public String getAuthId() {
    return authId;
  }

  public String getAuthToken() {
    return authToken;
  }
}
