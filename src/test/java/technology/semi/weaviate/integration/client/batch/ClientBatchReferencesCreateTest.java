package technology.semi.weaviate.integration.client.batch;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import technology.semi.weaviate.client.Config;
import technology.semi.weaviate.client.WeaviateClient;
import technology.semi.weaviate.client.base.Result;
import technology.semi.weaviate.client.v1.batch.api.ReferencesBatcher;
import technology.semi.weaviate.client.v1.batch.model.BatchReference;
import technology.semi.weaviate.client.v1.batch.model.BatchReferenceResponse;
import technology.semi.weaviate.client.v1.batch.model.ObjectGetResponse;
import technology.semi.weaviate.client.v1.data.model.WeaviateObject;
import technology.semi.weaviate.integration.client.WeaviateTestGenerics;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ClientBatchReferencesCreateTest {
  private static final String PIZZA_ID = "97fa5147-bdad-4d74-9a81-f8babc811b09";
  private static final Map<String, Object> PIZZA_PROPS = createFoodProperties("Doener", "A innovation, some say revolution, in the pizza industry.");
  private static final String SOUP_ID = "07473b34-0ab2-4120-882d-303d9e13f7af";
  private static final Map<String, Object> SOUP_PROPS = createFoodProperties("Beautiful", "Putting the game of letter soups to a whole new level.");
  private WeaviateClient client;
  private final WeaviateTestGenerics testGenerics = new WeaviateTestGenerics();

  @ClassRule
  public static DockerComposeContainer compose = new DockerComposeContainer(
          new File("src/test/resources/docker-compose-test.yaml")
  ).withExposedService("weaviate_1", 8080, Wait.forHttp("/v1/.well-known/ready").forStatusCode(200));

  @Before
  public void before() {
    String host = compose.getServiceHost("weaviate_1", 8080);
    Integer port = compose.getServicePort("weaviate_1", 8080);
    Config config = new Config("http", host + ":" + port);

    client = new WeaviateClient(config);
    testGenerics.createWeaviateTestSchemaFoodWithReferenceProperty(client);
  }

  @After
  public void after() {
    testGenerics.cleanupWeaviate(client);
  }

  @Test
  public void shouldCreateBatchReferences() {
    // given
    Result<ObjectGetResponse[]> batchResult = client.batch().objectsBatcher().withObjects(new WeaviateObject[] {
      WeaviateObject.builder()
        .id(PIZZA_ID)
        .className("Pizza")
        .properties(PIZZA_PROPS)
        .build(),
      WeaviateObject.builder()
        .id(SOUP_ID)
        .className("Soup")
        .properties(SOUP_PROPS)
        .build()
    }).run();

    assertThat(batchResult).isNotNull()
        .returns(false, Result::hasErrors);
    assertThat(batchResult.getResult()).hasSize(2);

    String fromPizza = String.format("weaviate://localhost/%s/%s/otherFoods", "Pizza", PIZZA_ID);
    String fromSoup = String.format("weaviate://localhost/%s/%s/otherFoods", "Soup", SOUP_ID);
    String toPizza = String.format("weaviate://localhost/%s/%s", "Pizza", PIZZA_ID);
    String toSoup = String.format("weaviate://localhost/%s/%s", "Soup", SOUP_ID);

    BatchReference refPizzaToSoup = BatchReference.builder().from(fromPizza).to(toSoup).build();
    BatchReference refSoupToPizza = BatchReference.builder().from(fromSoup).to(toPizza).build();
    BatchReference refPizzaToPizza = client.batch().referencePayloadBuilder()
      .withFromID(PIZZA_ID)
      .withFromClassName("Pizza")
      .withFromRefProp("otherFoods")
      .withToID(PIZZA_ID)
      .withToClassName("Pizza")
      .payload();
    BatchReference refSoupToSoup = client.batch().referencePayloadBuilder()
      .withFromID(SOUP_ID)
      .withFromClassName("Soup")
      .withFromRefProp("otherFoods")
      .withToID(SOUP_ID)
      .withToClassName("Soup")
      .payload();

    // when
    Result<BatchReferenceResponse[]> refsResult = client.batch().referencesBatcher()
      .withReferences(refPizzaToSoup, refSoupToPizza, refPizzaToPizza, refSoupToSoup)
      .run();

    // then
    assertThat(refsResult).isNotNull()
      .returns(false, Result::hasErrors);
    assertThat(refsResult.getResult()).hasSize(4);
    assertThat(refsResult.getResult())
      .extracting(BatchReferenceResponse::getFrom)
      .containsExactlyInAnyOrder(fromPizza, fromPizza, fromSoup, fromSoup);
    assertThat(refsResult.getResult())
      .extracting(BatchReferenceResponse::getTo)
      .containsExactlyInAnyOrder(toPizza, toPizza, toSoup, toSoup);

    Result<List<WeaviateObject>> pizzaResult = client.data().objectsGetter().withID(PIZZA_ID).withClassName("Pizza").run();

    assertThat(pizzaResult).isNotNull()
      .returns(false, Result::hasErrors);
    assertThat(pizzaResult.getResult()).hasSize(1);

    WeaviateObject pizza = pizzaResult.getResult().get(0);
    assertThat(pizza.getId()).isEqualTo(PIZZA_ID);
    assertThat(pizza.getProperties()).isNotNull();

    Object pizzaOtherFoods = pizza.getProperties().get("otherFoods");
    assertThat(pizzaOtherFoods).isNotNull()
      .isInstanceOf(List.class)
      .asList().hasSize(2)
      .extracting(map -> ((Map<String, String>)map).get("beacon"))
      .containsExactlyInAnyOrder(toPizza, toSoup);

    Result<List<WeaviateObject>> soupResult = client.data().objectsGetter().withID(SOUP_ID).withClassName("Soup").run();

    assertThat(soupResult).isNotNull()
      .returns(false, Result::hasErrors);
    assertThat(soupResult.getResult()).hasSize(1);

    WeaviateObject soup = soupResult.getResult().get(0);
    assertThat(soup.getId()).isEqualTo(SOUP_ID);
    assertThat(soup.getProperties()).isNotNull();

    Object soupOtherFoods = soup.getProperties().get("otherFoods");
    assertThat(soupOtherFoods).isNotNull()
      .isInstanceOf(List.class)
      .asList().hasSize(2)
      .extracting(map -> ((Map<String, Object>)map).get("beacon"))
      .containsExactlyInAnyOrder(toPizza, toSoup);





//    // classT
//    String classTID = PIZZA_ID;
//    // classA
//    String classAID = SOUP_ID;
//    // references
//    BatchReference refTtoA = BatchReference.builder()
//            .from("weaviate://localhost/Pizza/97fa5147-bdad-4d74-9a81-f8babc811b09/otherFoods")
//            .to("weaviate://localhost/Soup/07473b34-0ab2-4120-882d-303d9e13f7af")
//            .build();
//    BatchReference refAtoT = BatchReference.builder()
//            .from("weaviate://localhost/Soup/07473b34-0ab2-4120-882d-303d9e13f7af/otherFoods")
//            .to("weaviate://localhost/Pizza/97fa5147-bdad-4d74-9a81-f8babc811b09")
//            .build();
//    // when
//    Result<WeaviateObject> classT = client.data().creator()
//            .withClassName("Pizza")
//            .withID(classTID)
//            .withProperties(new HashMap<String, Object>() {{
//              put("name", "Doener");
//              put("description", "A innovation, some say revolution, in the pizza industry.");
//            }})
//            .run();
//    Result<WeaviateObject> classA = client.data().creator()
//            .withClassName("Soup")
//            .withID(classAID)
//            .withProperties(new HashMap<String, Object>() {{
//              put("name", "Beautiful");
//              put("description", "Putting the game of letter soups to a whole new level.");
//            }})
//            .run();
////    Result<ObjectGetResponse[]> createClassT = client.batch().objectsBatcher().withObjects(classT.getResult()).run();
////    Result<ObjectGetResponse[]> createClassA = client.batch().objectsBatcher().withObjects(classA.getResult()).run();
//    BatchReference refTtoT = client.batch().referencePayloadBuilder()
//            .withFromClassName("Pizza")
//            .withFromRefProp("otherFoods")
//            .withFromID(classTID)
//            .withToID(classTID)
//            .withToClassName("Pizza")
//            .payload();
//    BatchReference refAtoA = client.batch().referencePayloadBuilder()
//            .withFromClassName("Soup")
//            .withFromRefProp("otherFoods")
//            .withFromID(classAID)
//            .withToID(classAID)
//            .withToClassName("Soup")
//            .payload();
//    Result<BatchReferenceResponse[]> refResult = client.batch().referencesBatcher()
//            .withReferences(refTtoA, refTtoT, refAtoT, refAtoA)
//            .run();
//    Result<List<WeaviateObject>> objT = client.data().objectsGetter().withID(classTID).withClassName("Pizza").run();
//    Result<List<WeaviateObject>> objA = client.data().objectsGetter().withID(classAID).withClassName("Soup").run();
//    // then
////    assertNotNull(createClassT);
////    assertNotNull(createClassT.getResult());
////    assertEquals(1, createClassT.getResult().length);
////    assertNotNull(createClassA);
////    assertNotNull(createClassA.getResult());
////    assertEquals(1, createClassA.getResult().length);
//    assertNotNull(refTtoT);
//    assertNotNull(refAtoA);
//    assertNotNull(refResult);
//    assertNotNull(refResult.getResult());
//    assertEquals(4, refResult.getResult().length);
//    // assert objT
//    assertNotNull(objT);
//    assertNotNull(objT.getResult());
//    assertEquals(1, objT.getResult().size());
//    assertEquals(classTID, objT.getResult().get(0).getId());
//    assertNotNull(classTID, objT.getResult().get(0).getProperties());
//    assertNotNull(classTID, objT.getResult().get(0).getProperties().get("otherFoods"));
//    Assert.assertTrue(objT.getResult().get(0).getProperties().get("otherFoods") instanceof List);
//    List otherFoods = (List) objT.getResult().get(0).getProperties().get("otherFoods");
//    Assert.assertEquals(2, otherFoods.size());
//    Assert.assertTrue(otherFoods.get(0) instanceof Map);
//    Map otherFood0 = (Map) otherFoods.get(0);
//    Map otherFood1 = (Map) otherFoods.get(1);
//    List beacons = Stream.of(otherFood0.get("beacon"), otherFood1.get("beacon")).collect(Collectors.toList());
//    Assert.assertTrue(beacons.contains("weaviate://localhost/Soup/07473b34-0ab2-4120-882d-303d9e13f7af"));
//    Assert.assertTrue(beacons.contains("weaviate://localhost/Pizza/97fa5147-bdad-4d74-9a81-f8babc811b09"));
//    // assert objA
//    assertNotNull(objA);
//    assertNotNull(objA.getResult());
//    assertEquals(1, objA.getResult().size());
//    assertEquals(classAID, objA.getResult().get(0).getId());
//    assertNotNull(classAID, objA.getResult().get(0).getProperties());
//    assertNotNull(classAID, objA.getResult().get(0).getProperties().get("otherFoods"));
//    Assert.assertTrue(objA.getResult().get(0).getProperties().get("otherFoods") instanceof List);
//    otherFoods = (List) objA.getResult().get(0).getProperties().get("otherFoods");
//    Assert.assertEquals(2, otherFoods.size());
//    Assert.assertTrue(otherFoods.get(0) instanceof Map);
//    otherFood0 = (Map) otherFoods.get(0);
//    otherFood1 = (Map) otherFoods.get(1);
//    beacons = Stream.of(otherFood0.get("beacon"), otherFood1.get("beacon")).collect(Collectors.toList());
//    Assert.assertTrue(beacons.contains("weaviate://localhost/Soup/07473b34-0ab2-4120-882d-303d9e13f7af"));
//    Assert.assertTrue(beacons.contains("weaviate://localhost/Pizza/97fa5147-bdad-4d74-9a81-f8babc811b09"));
  }

  @Test
  public void shouldCreateAutoBatchReferences() {
    // given
    Result<ObjectGetResponse[]> resBatch = client.batch().objectsBatcher().withObjects(new WeaviateObject[] {
      WeaviateObject.builder()
        .id(PIZZA_ID)
        .className("Pizza")
        .properties(PIZZA_PROPS)
        .build(),
      WeaviateObject.builder()
        .id(SOUP_ID)
        .className("Soup")
        .properties(SOUP_PROPS)
        .build()
    }).run();

    assertThat(resBatch).isNotNull()
      .returns(false, Result::hasErrors);
    assertThat(resBatch.getResult()).hasSize(2);

    String fromPizza = String.format("weaviate://localhost/%s/%s/otherFoods", "Pizza", PIZZA_ID);
    String fromSoup = String.format("weaviate://localhost/%s/%s/otherFoods", "Soup", SOUP_ID);
    String toPizza = String.format("weaviate://localhost/%s/%s", "Pizza", PIZZA_ID);
    String toSoup = String.format("weaviate://localhost/%s/%s", "Soup", SOUP_ID);

    BatchReference refPizzaToSoup = BatchReference.builder().from(fromPizza).to(toSoup).build();
    BatchReference refSoupToPizza = BatchReference.builder().from(fromSoup).to(toPizza).build();
    BatchReference refPizzaToPizza = client.batch().referencePayloadBuilder()
      .withFromID(PIZZA_ID)
      .withFromClassName("Pizza")
      .withFromRefProp("otherFoods")
      .withToID(PIZZA_ID)
      .withToClassName("Pizza")
      .payload();
    BatchReference refSoupToSoup = client.batch().referencePayloadBuilder()
      .withFromID(SOUP_ID)
      .withFromClassName("Soup")
      .withFromRefProp("otherFoods")
      .withToID(SOUP_ID)
      .withToClassName("Soup")
      .payload();

    // when
    List<Result<BatchReferenceResponse[]>> resultsReferences = Collections.synchronizedList(new ArrayList<>(2));
    ReferencesBatcher.AutoBatchConfig autoBatchConfig = ReferencesBatcher.AutoBatchConfig.defaultConfig()
      .batchSize(2)
      .callback(resultsReferences::add)
      .build();

    client.batch().referencesAutoBatcher(autoBatchConfig)
      .withReferences(refPizzaToSoup, refSoupToPizza, refPizzaToPizza, refSoupToSoup)
      .flush();

    // then
    assertThat(resultsReferences).hasSize(2);
    for (Result<BatchReferenceResponse[]> resReferences: resultsReferences) {
      assertThat(resReferences).isNotNull()
        .returns(false, Result::hasErrors);
      assertThat(resReferences.getResult()).hasSize(2);
      assertThat(resReferences.getResult())
        .extracting(BatchReferenceResponse::getFrom)
        .containsExactlyInAnyOrder(fromPizza, fromSoup);
      assertThat(resReferences.getResult())
        .extracting(BatchReferenceResponse::getTo)
        .containsExactlyInAnyOrder(toPizza, toSoup);
    }

    Result<List<WeaviateObject>> resPizza = client.data().objectsGetter().withID(PIZZA_ID).withClassName("Pizza").run();

    assertThat(resPizza).isNotNull()
      .returns(false, Result::hasErrors);
    assertThat(resPizza.getResult()).hasSize(1);

    WeaviateObject pizza = resPizza.getResult().get(0);
    assertThat(pizza.getId()).isEqualTo(PIZZA_ID);
    assertThat(pizza.getProperties()).isNotNull();

    Object pizzaOtherFoods = pizza.getProperties().get("otherFoods");
    assertThat(pizzaOtherFoods).isNotNull()
      .isInstanceOf(List.class)
      .asList().hasSize(2)
      .extracting(map -> ((Map<String, String>)map).get("beacon"))
      .containsExactlyInAnyOrder(toPizza, toSoup);

    Result<List<WeaviateObject>> resSoup = client.data().objectsGetter().withID(SOUP_ID).withClassName("Soup").run();

    assertThat(resSoup).isNotNull()
      .returns(false, Result::hasErrors);
    assertThat(resSoup.getResult()).hasSize(1);

    WeaviateObject soup = resSoup.getResult().get(0);
    assertThat(soup.getId()).isEqualTo(SOUP_ID);
    assertThat(soup.getProperties()).isNotNull();

    Object soupOtherFoods = soup.getProperties().get("otherFoods");
    assertThat(soupOtherFoods).isNotNull()
      .isInstanceOf(List.class)
      .asList().hasSize(2)
      .extracting(map -> ((Map<String, Object>)map).get("beacon"))
      .containsExactlyInAnyOrder(toPizza, toSoup);
  }

  private static Map<String, Object> createFoodProperties(String name, String description) {
    Map<String, Object> props = new HashMap<>();
    props.put("name", name);
    props.put("description", description);

    return props;
  }
}
