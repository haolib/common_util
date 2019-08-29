
import com.google.common.base.Supplier;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.Cache.ValueWrapper;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheElement;
import org.springframework.data.redis.cache.RedisCacheKey;

/**
 * 缓存工具类
 */
public enum CacheHelper {
  INSTANCE;

  private LoadingCache<SimpleEntry<String, String>, ReentrantLock> lockCache = CacheBuilder.newBuilder()
      .expireAfterAccess(1, TimeUnit.MINUTES)
      .build(new CacheLoader<SimpleEntry<String, String>, ReentrantLock>() {
        @Override
        public ReentrantLock load(@Nonnull SimpleEntry<String, String> key) {
          return new ReentrantLock();
        }
      });
  private Logger logger = LoggerFactory.getLogger(this.getClass());
  /**
   * @param <RESULT> 缓存结果
   * @param key 缓存key
   * @param loader 缓存不存在的时候，提供数据的加载器
   * @param cache 缓存
   * @param expireSeconds 过期时间，为空则使用默认配置
   * @param forceUpdate
   */
  public <RESULT> RESULT get(String key, Supplier<RESULT> loader, Cache cache, Long expireSeconds, boolean forceUpdate) {
    ValueWrapper valueWrapper = cache.get(key);
    if (forceUpdate || valueWrapper == null || valueWrapper.get() == null) {
      SimpleEntry<String, String> cacheKey = new SimpleEntry<>(cache.getName(), key);
      ReentrantLock lock = null;
      try {
        lock = lockCache.getUnchecked(cacheKey);
        lock.lock();

        valueWrapper = cache.get(key);
        if (forceUpdate || valueWrapper == null || valueWrapper.get() == null) {
          RESULT result = loader.get();
          if (result == null) {
            return null;
          } else {
            if (expireSeconds != null && cache instanceof RedisCache) {
              ((RedisCache) cache).put(new RedisCacheElement(new RedisCacheKey(key.getBytes()).usePrefix((cache.getName() + ":").getBytes()), result)
                  .expireAfter(expireSeconds));
            } else {
              cache.put(key, result);
            }
            return result;
          }
        }

      } catch (Exception e) {
        logger.error("",e);
      } finally {
        try {
          if (lock != null) {
            lock.unlock();
            lockCache.invalidate(cacheKey);
          }
        } catch (Exception e) {
          // DO NOTHING
        }
      }
    }
    //noinspection unchecked
    return (RESULT) valueWrapper.get();
  }

}
