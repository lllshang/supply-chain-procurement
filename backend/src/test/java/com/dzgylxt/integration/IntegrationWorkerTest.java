package com.dzgylxt.integration;

import com.dzgylxt.entity.integration.OutboxEvent;
import com.dzgylxt.enums.OutboxStatus;
import com.dzgylxt.mapper.integration.OutboxEventMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationWorkerTest {

    @Mock
    private OutboxEventMapper outboxEventMapper;

    @Mock
    private Adapter adapter;

    @InjectMocks
    private IntegrationWorker worker;

    @Test
    void poll_marksSentWhenAdapterSucceeds() {
        OutboxEvent event = new OutboxEvent();
        event.setId(1L);
        event.setStatus(OutboxStatus.PENDING);
        when(outboxEventMapper.selectList(any())).thenReturn(List.of(event));
        when(adapter.deliver(event)).thenReturn(true);

        worker.poll();

        verify(adapter).deliver(event);
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventMapper).updateById(captor.capture());
        assertEquals(OutboxStatus.SENT, captor.getValue().getStatus());
    }

    @Test
    void poll_marksRetryWhenAdapterFails() {
        OutboxEvent event = new OutboxEvent();
        event.setId(2L);
        event.setStatus(OutboxStatus.PENDING);
        when(outboxEventMapper.selectList(any())).thenReturn(List.of(event));
        when(adapter.deliver(event)).thenReturn(false);

        worker.poll();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventMapper).updateById(captor.capture());
        assertEquals(OutboxStatus.RETRY, captor.getValue().getStatus());
    }
}
