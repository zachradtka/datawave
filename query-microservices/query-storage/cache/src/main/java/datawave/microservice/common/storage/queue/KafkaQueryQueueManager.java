package datawave.microservice.common.storage.queue;

import datawave.microservice.common.storage.QueryPool;
import datawave.microservice.common.storage.QueryQueueListener;
import datawave.microservice.common.storage.QueryQueueManager;
import datawave.microservice.common.storage.QueryTask;
import datawave.microservice.common.storage.QueryTaskNotification;
import datawave.microservice.common.storage.TaskKey;
import datawave.microservice.common.storage.config.QueryStorageProperties;
import org.apache.log4j.Logger;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

public class KafkaQueryQueueManager implements QueryQueueManager {
    private static final Logger log = Logger.getLogger(QueryQueueManager.class);
    
    // A mapping of query pools to routing keys
    private Map<QueryPool,String> exchanges = new HashMap<>();
    
    @Autowired
    TestMessageConsumer testMessageConsumer;
    
    @Autowired
    QueryStorageProperties properties;
    
    /**
     * Passes task notifications to the messaging infrastructure.
     *
     * @param taskNotification
     *            The task notification to be sent
     */
    @Override
    public void sendMessage(QueryTaskNotification taskNotification) {
        ensureQueueCreated(taskNotification.getTaskKey().getQueryPool());
        
        String exchangeName = taskNotification.getTaskKey().getQueryPool().getName();
        if (log.isDebugEnabled()) {
            log.debug("Publishing message to " + exchangeName + " for " + taskNotification.getTaskKey().toKey());
        }
        
        // TODO: rabbitTemplate.convertAndSend(exchangeName, taskNotification.getTaskKey().toKey(), taskNotification);
    }
    
    /**
     * Ensure a queue is created for a query results queue. This will create an exchange, a queue, and a binding between them for the results queue.
     *
     * @param queryId
     *            the query ID
     */
    @Override
    public void ensureQueueCreated(UUID queryId) {
        // TODO
    }
    
    /**
     * This will send a result message. This will call ensureQueueCreated before sending the message.
     * <p>
     * TODO Should the result be more strongly typed?
     *
     * @param queryId
     *            the query ID
     * @param resultId
     *            a unique id for the result
     * @param result
     */
    @Override
    public void sendMessage(UUID queryId, String resultId, Object result) {
        // TODO
    }
    
    /**
     * Add a queue to a listener
     *
     * @param listenerId
     * @param queueName
     */
    @Override
    public void addQueueToListener(String listenerId, String queueName) {
        if (log.isDebugEnabled()) {
            log.debug("adding queue : " + queueName + " to listener with id : " + listenerId);
        }
        if (!checkQueueExistOnListener(listenerId, queueName)) {
            AbstractMessageListenerContainer listener = getMessageListenerContainerById(listenerId);
            if (listener != null) {
                if (log.isTraceEnabled()) {
                    log.trace("Found listener for " + listenerId);
                }
                listener.addQueueNames(queueName);
                if (log.isTraceEnabled()) {
                    log.trace("added queue : " + queueName + " to listener with id : " + listenerId);
                }
            }
        } else {
            if (log.isTraceEnabled()) {
                log.trace("given queue name : " + queueName + " already exists on given listener id : " + listenerId);
            }
        }
    }
    
    /**
     * Remove a queue from a listener
     *
     * @param listenerId
     * @param queueName
     */
    @Override
    public void removeQueueFromListener(String listenerId, String queueName) {
        if (log.isInfoEnabled()) {
            log.info("removing queue : " + queueName + " from listener : " + listenerId);
        }
        if (checkQueueExistOnListener(listenerId, queueName)) {
            this.getMessageListenerContainerById(listenerId).removeQueueNames(queueName);
        } else {
            if (log.isTraceEnabled()) {
                log.trace("given queue name : " + queueName + " not exist on given listener id : " + listenerId);
            }
        }
    }
    
    /**
     * Check whether a queue exists on a listener
     *
     * @param listenerId
     * @param queueName
     * @return true if listening, false if not
     */
    private boolean checkQueueExistOnListener(String listenerId, String queueName) {
        try {
            if (log.isTraceEnabled()) {
                log.trace("checking queueName : " + queueName + " exist on listener id : " + listenerId);
            }
            AbstractMessageListenerContainer container = this.getMessageListenerContainerById(listenerId);
            String[] queueNames = (container == null ? null : container.getQueueNames());
            if (queueNames != null) {
                if (log.isTraceEnabled()) {
                    log.trace("checking " + queueName + " exist on active queues " + Arrays.toString(queueNames));
                }
                for (String name : queueNames) {
                    if (name.equals(queueName)) {
                        log.trace("queue name exist on listener, returning true");
                        return true;
                    }
                }
                log.trace("queue name does not exist on listener, returning false");
                return false;
            } else {
                log.trace("there is no queue exist on listener");
                return false;
            }
        } catch (Exception e) {
            log.error("Error on checking queue exist on listener", e);
            return false;
        }
    }
    
    /**
     * Get the listener given a listener id
     *
     * @param listenerId
     * @return the listener
     */
    private AbstractMessageListenerContainer getMessageListenerContainerById(String listenerId) {
        if (log.isTraceEnabled()) {
            log.trace("getting message listener container by id : " + listenerId);
        }
        // TODO return ((AbstractMessageListenerContainer) this.rabbitListenerEndpointRegistry.getListenerContainer(listenerId));
        return null;
    }
    
    /**
     * Create a listener for a specified listener id
     *
     * @param listenerId
     *            The listener id
     * @return a query queue listener
     */
    @Override
    public QueryQueueListener createListener(String listenerId) {
        return null;
    }
    
    /**
     * Ensure a queue is created for a given pool
     *
     * @param queryPool
     */
    @Override
    public void ensureQueueCreated(QueryPool queryPool) {
        String exchangeQueueName = queryPool.getName();
        TaskKey taskKey = new TaskKey(null, queryPool, null, null);
        if (exchanges.get(queryPool) == null) {
            synchronized (exchanges) {
                if (exchanges.get(queryPool) == null) {
                    if (log.isInfoEnabled()) {
                        log.debug("Creating exchange/queue " + exchangeQueueName + " with routing key " + taskKey.toRoutingKey());
                    }
                    // TopicExchange exchange = new TopicExchange(exchangeQueueName, properties.isSynchStorage(), false);
                    // Queue queue = new Queue(exchangeQueueName, properties.isSynchStorage(), false, false);
                    // Binding binding = BindingBuilder.bind(queue).to(exchange).with(taskKey.toRoutingKey());
                    // // TODO rabbitAdmin.declareExchange(exchange);
                    // // TODO rabbitAdmin.declareQueue(queue);
                    // // TODO rabbitAdmin.declareBinding(binding);
                    //
                    // if (log.isInfoEnabled()) {
                    // log.debug("Sending test message to verify exchange/queue " + exchangeQueueName);
                    // }
                    // // add our test listener to the queue and wait for a test message
                    // addQueueToListener(testMessageConsumer.getListenerId(), exchangeQueueName);
                    //// QueryTaskNotification testNotification = new QueryTaskNotification(new TaskKey(UUID.randomUUID(), queryPool, UUID.randomUUID(),
                    // "None"),
                    //// QueryTask.QUERY_ACTION.TEST);
                    // // TODO rabbitTemplate.convertAndSend(exchangeQueueName, testNotification.getTaskKey().toKey(), testNotification);
                    // QueryTaskNotification notification = testMessageConsumer.receive();
                    // removeQueueFromListener(testMessageConsumer.getListenerId(), exchangeQueueName);
                    // if (notification == null) {
                    // throw new RuntimeException("Unable to verify that queue and exchange were created for " + exchangeQueueName);
                    // }
                    
                    exchanges.put(queryPool, taskKey.toRoutingKey());
                }
            }
        }
    }
    
    @Component
    public static class TestMessageConsumer {
        private static final String LISTENER_ID = "KafkaQueryQueueManagerTestListener";
        
        // default wait for 1 minute
        private static final long WAIT_MS_DEFAULT = 60L * 1000L;
        
        @Autowired
        private RabbitTemplate rabbitTemplate;
        
        private java.util.Queue<QueryTaskNotification> notificationQueue = new ArrayBlockingQueue<>(10);
        
        @RabbitListener(id = LISTENER_ID, autoStartup = "true")
        public void processMessage(QueryTaskNotification notification) {
            // determine if this is a test message
            if (notification.getAction().equals(QueryTask.QUERY_ACTION.TEST)) {
                notificationQueue.add(notification);
            } else {
                // requeue, this was not a test message
                rabbitTemplate.convertAndSend(notification.getTaskKey().getQueryPool().getName(), notification.getTaskKey().toKey(), notification);
            }
        }
        
        public String getListenerId() {
            return LISTENER_ID;
        }
        
        public QueryTaskNotification receive() {
            return receive(WAIT_MS_DEFAULT);
        }
        
        public QueryTaskNotification receive(long waitMs) {
            long start = System.currentTimeMillis();
            while (notificationQueue.isEmpty() && ((System.currentTimeMillis() - start) < waitMs)) {
                try {
                    Thread.sleep(1L);
                } catch (InterruptedException e) {
                    break;
                }
            }
            if (notificationQueue.isEmpty()) {
                return null;
            } else {
                return notificationQueue.remove();
            }
        }
    }
    
}
