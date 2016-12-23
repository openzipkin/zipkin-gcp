package com.google.cloud.trace.zipkin;

import com.google.auto.service.AutoService;
import io.grpc.ManagedChannelProvider;
import io.grpc.netty.NettyChannelBuilder;

/**
 * Allows non-ssl grpc connections for {@link NettyChannelBuilder} instances.
 */
@AutoService(ManagedChannelProvider.class)
public class PlainTextNettyChannelProvider extends ManagedChannelProvider
{
  @Override
  public boolean isAvailable()
  {
    return true;
  }

  @Override
  public int priority()
  {
    //put it in front of all other providers: NettyChannelProvider/OkHttpChannelProvider
    return Integer.MAX_VALUE;
  }

  @Override
  public NettyChannelBuilder builderForAddress(String name, int port)
  {
    return NettyChannelBuilder.forAddress(name, port).usePlaintext(true);
  }

  @Override
  public NettyChannelBuilder builderForTarget(String target)
  {
    return NettyChannelBuilder.forTarget(target).usePlaintext(true);
  }
}
