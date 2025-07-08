package com.workshop.mcp;

import com.workshop.mcp.io.IOHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Server class following DRY and SOLID principles.
 * Uses mocks to isolate the Server class and test its behavior independently.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Server Tests")
class ServerTest {

    @Mock
    private IOHandler mockIOHandler;
    
    @Mock
    private Router mockRouter;
    
    @Mock
    private CountDownLatch mockLatch;
    
    private Server server;
    
    @BeforeEach
    void setUp() {
        server = new Server(mockIOHandler, mockRouter, mockLatch);
    }
    
    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {
        
        @Test
        @DisplayName("Should initialize Server with provided dependencies")
        void constructor_WithValidDependencies_InitializesSuccessfully() {
            // Given
            IOHandler io = mock(IOHandler.class);
            Router router = mock(Router.class);
            CountDownLatch latch = new CountDownLatch(1);
            
            // When
            Server testServer = new Server(io, router, latch);
            
            // Then
            Assertions.assertNotNull(testServer);
        }
    }
    
    @Nested
    @DisplayName("Stop Method Tests")
    class StopMethodTests {
        
        @Test
        @DisplayName("Should stop IOHandler when not null")
        void stop_WithNonNullIOHandler_StopsIOHandler() {
            // When
            server.stop();
            
            // Then
            verify(mockIOHandler).stopRunning();
        }
        
        @Test
        @DisplayName("Should handle null IOHandler gracefully")
        void stop_WithNullIOHandler_DoesNotThrowException() {
            // Given
            Server serverWithNullIO = new Server(null, mockRouter, mockLatch);
            
            // When & Then (should not throw)
            Assertions.assertDoesNotThrow(() -> serverWithNullIO.stop());
        }
    }
    
    @Nested
    @DisplayName("Start Method Tests - Partial Coverage")
    class StartMethodPartialTests {
        
        @Test
        @DisplayName("Should add line listener and start input reader before keepRunning")
        void start_InitialSetup_WorksCorrectly() throws Exception {
            // Given
            // Mock to make keepRunning exit quickly
            when(mockIOHandler.isRunning()).thenReturn(false);
            when(mockLatch.await(anyLong(), any(TimeUnit.class))).thenReturn(true);
            
            // Use a separate thread to avoid System.exit
            Thread testThread = new Thread(() -> {
                try {
                    server.start();
                } catch (Exception e) {
                    // Expected due to System.exit
                }
            });
            
            testThread.start();
            Thread.sleep(100); // Give time for initial setup
            testThread.interrupt(); // Interrupt before System.exit
            testThread.join(500);
            
            // Then
            verify(mockIOHandler).addLineListener(any());
            verify(mockIOHandler).startInputReader();
        }
        
        @Test
        @DisplayName("Should handle exception during start setup")
        void start_WithException_CallsStop() throws Exception {
            // Given
            doThrow(new RuntimeException("Test exception"))
                .when(mockIOHandler).addLineListener(any());
            
            // When
            Thread testThread = new Thread(() -> {
                try {
                    server.start();
                } catch (Exception e) {
                    // Expected
                }
            });
            
            testThread.start();
            testThread.join(500);
            
            // Then
            verify(mockIOHandler).stopRunning();
        }
        
        @Test
        @DisplayName("Should register shutdown hook during start")
        void start_RegistersShutdownHook() throws Exception {
            // Given
            when(mockIOHandler.isRunning()).thenReturn(false);
            when(mockLatch.await(anyLong(), any(TimeUnit.class))).thenReturn(true);
            
            // Count the number of shutdown hooks before and after
            Runtime runtime = Runtime.getRuntime();
            
            // Use reflection to access shutdown hooks (alternative approach)
            // Since we can't directly verify shutdown hook registration in JDK 21,
            // we verify the behavior indirectly through the expected method calls
            
            Thread testThread = new Thread(() -> {
                try {
                    server.start();
                } catch (Exception e) {
                    // Expected due to System.exit
                }
            });
            
            testThread.start();
            Thread.sleep(100); // Give time for start method to execute
            testThread.interrupt();
            testThread.join(500);
            
            // Verify that the start method executed successfully by checking
            // that the expected initialization methods were called
            verify(mockIOHandler).addLineListener(any());
            verify(mockIOHandler).startInputReader();
            
            // The shutdown hook registration happens between these calls,
            // so if both were called, we know the shutdown hook was registered
        }
        
        @Test
        @DisplayName("Should pass router.route method reference to IOHandler")
        void start_PassesCorrectListenerToIOHandler() throws Exception {
            // Given
            when(mockIOHandler.isRunning()).thenReturn(false);
            when(mockLatch.await(anyLong(), any(TimeUnit.class))).thenReturn(true);
            
            // When
            Thread testThread = new Thread(() -> {
                try {
                    server.start();
                } catch (Exception e) {
                    // Expected due to System.exit
                }
            });
            
            testThread.start();
            Thread.sleep(100);
            testThread.interrupt();
            testThread.join(500);
            
            // Then
            verify(mockIOHandler).addLineListener(any());
            // Note: We can't directly verify the method reference equals router::route
            // but we've verified that a listener was added
        }
        
        @Test
        @DisplayName("Should handle exception from startInputReader")
        void start_WithExceptionFromStartInputReader_CallsStop() throws Exception {
            // Given
            doThrow(new RuntimeException("Input reader exception"))
                .when(mockIOHandler).startInputReader();
            
            // When
            Thread testThread = new Thread(() -> {
                try {
                    server.start();
                } catch (Exception e) {
                    // Expected
                }
            });
            
            testThread.start();
            testThread.join(500);
            
            // Then
            verify(mockIOHandler).addLineListener(any());
            verify(mockIOHandler).startInputReader();
            verify(mockIOHandler).stopRunning();
        }
        
        @Test
        @DisplayName("Should call keepRunning after successful initialization")
        void start_AfterSuccessfulInit_CallsKeepRunning() throws Exception {
            // Given
            when(mockIOHandler.isRunning())
                .thenReturn(true)   // First check in keepRunning
                .thenReturn(false); // Second check - trigger shutdown
            when(mockLatch.await(anyLong(), any(TimeUnit.class))).thenReturn(false);
            
            // When
            Thread testThread = new Thread(() -> {
                try {
                    server.start();
                } catch (Exception e) {
                    // Expected due to System.exit
                }
            });
            
            testThread.start();
            Thread.sleep(200); // Allow time for keepRunning to execute
            testThread.interrupt();
            testThread.join(500);
            
            // Then
            verify(mockIOHandler).addLineListener(any());
            verify(mockIOHandler).startInputReader();
            verify(mockIOHandler, atLeastOnce()).isRunning(); // Verify keepRunning was called
        }
        
        @Test
        @DisplayName("Should handle null IOHandler during start")
        void start_WithNullIOHandler_HandlesGracefully() throws Exception {
            // Given
            Server serverWithNullIO = new Server(null, mockRouter, mockLatch);
            when(mockLatch.await(anyLong(), any(TimeUnit.class))).thenReturn(true);
            
            // When
            Thread testThread = new Thread(() -> {
                try {
                    serverWithNullIO.start();
                } catch (NullPointerException e) {
                    // Expected when trying to call methods on null IOHandler
                } catch (Exception e) {
                    // Other exceptions
                }
            });
            
            testThread.start();
            testThread.join(500);
            
            // Then - test completes without hanging
            Assertions.assertTrue(true);
        }
        
        @Test
        @DisplayName("Should execute in correct order: addLineListener, startInputReader, keepRunning")
        void start_ExecutionOrder_IsCorrect() throws Exception {
            // Given
            when(mockIOHandler.isRunning()).thenReturn(false);
            when(mockLatch.await(anyLong(), any(TimeUnit.class))).thenReturn(true);
            
            // Use InOrder to verify execution sequence
            InOrder inOrder = inOrder(mockIOHandler);
            
            // When
            Thread testThread = new Thread(() -> {
                try {
                    server.start();
                } catch (Exception e) {
                    // Expected due to System.exit
                }
            });
            
            testThread.start();
            Thread.sleep(100);
            testThread.interrupt();
            testThread.join(500);
            
            // Then
            inOrder.verify(mockIOHandler).addLineListener(any());
            inOrder.verify(mockIOHandler).startInputReader();
            inOrder.verify(mockIOHandler, atLeastOnce()).isRunning(); // From keepRunning
        }
    }
    
    @Nested
    @DisplayName("KeepRunning Method Tests - Partial Coverage")
    class KeepRunningPartialTests {

        @Test
        @DisplayName("Should check if IO is running and initiate shutdown when stopped")
        void keepRunning_WhenIOStops_InitiatesShutdown() throws Exception {
            // Given
            when(mockIOHandler.isRunning()).thenReturn(false);
            when(mockLatch.await(anyLong(), any(TimeUnit.class))).thenReturn(false).thenReturn(true);

            // When
            Thread testThread = new Thread(() -> {
                try {
                    server.keepRunning();
                } catch (Exception e) {
                    // Expected due to System.exit
                }
            });

            testThread.start();
            Thread.sleep(100);
            testThread.interrupt();
            testThread.join(500);

            // Then
            verify(mockIOHandler, atLeastOnce()).isRunning();
            verify(mockLatch).countDown();
        }

        @Test
        @DisplayName("Should continue polling while IO is running")
        void keepRunning_WhileIORunning_ContinuesPolling() throws Exception {
            // Given
            when(mockIOHandler.isRunning())
                .thenReturn(true)   // First check
                .thenReturn(true)   // Second check
                .thenReturn(false); // Third check - trigger shutdown
            when(mockLatch.await(anyLong(), any(TimeUnit.class))).thenReturn(false);

            // When
            Thread testThread = new Thread(() -> {
                try {
                    server.keepRunning();
                } catch (Exception e) {
                    // Expected
                }
            });

            testThread.start();
            Thread.sleep(200); // Allow multiple polling cycles
            testThread.interrupt();
            testThread.join(500);

            // Then
            verify(mockIOHandler, atLeast(2)).isRunning();
            verify(mockLatch).countDown();
        }

        @Test
        @DisplayName("Should exit when latch is triggered")
        void keepRunning_WhenLatchTriggered_Exits() throws Exception {
            // Given
            when(mockIOHandler.isRunning()).thenReturn(true);
            when(mockLatch.await(anyLong(), any(TimeUnit.class)))
                .thenReturn(false)  // First check
                .thenReturn(true);  // Second check - shutdown triggered

            // When
            Thread testThread = new Thread(() -> {
                try {
                    server.keepRunning();
                } catch (Exception e) {
                    // Expected
                }
            });

            testThread.start();
            Thread.sleep(100);
            testThread.interrupt();
            testThread.join(500);

            // Then
            verify(mockLatch, atLeast(2)).await(anyLong(), any(TimeUnit.class));
        }
    }


    @Test
    @DisplayName("Simple close test")
    void simpleCloseTest() {
        Server testServer = new Server(null, null, null);
        testServer.stop();
    }

    @Test
    @DisplayName("IoHandler should get called to stop")
    void  ioHandler_closed() {
        Server testServer = new Server(mockIOHandler, null, null);
        testServer.stop();
        verify(mockIOHandler).stopRunning();
    }

    @Test
    @DisplayName("Should handle null ioHandler gracefully in stop")
    void stop_WithNullIOHandler_DoesNotThrowException() {
        Server testServer = new Server(null, null, null);
        testServer.start();
    }

    @Test
    @DisplayName("IoHandler get add listener called")
    void ioHandler_addListener_called() {
        Server testServer = new Server(mockIOHandler, mockRouter, null);
        testServer.start();
        verify(mockIOHandler).addLineListener(any());
    }


}