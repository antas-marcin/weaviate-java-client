package technology.semi.weaviate.client.v1.misc.api;

import technology.semi.weaviate.client.base.BaseClient;
import technology.semi.weaviate.client.base.Client;
import technology.semi.weaviate.client.Config;
import technology.semi.weaviate.client.base.Response;
import technology.semi.weaviate.client.v1.misc.api.model.Meta;

public class MetaGetter extends BaseClient<Meta> implements Client<Meta> {

  public MetaGetter(Config config) {
    super(config);
  }

  @Override
  public Meta Do() {
    Response<Meta> resp = sendGetRequest("/meta", Meta.class);
    return resp.getBody();
  }
}
