import { computed } from 'vue';
import { useWebSocket } from '@vueuse/core';

export const useChatStore = defineStore(SetupStoreId.Chat, () => {
  const conversationId = ref<string>('');
  const input = ref<Api.Chat.Input>({ message: '' });

  const list = ref<Api.Chat.Message[]>([]);

  const authStore = useAuthStore();

  const wsUrl = computed(() => {
    const token = authStore.token?.trim();
    return token ? `/proxy-ws/chat/${encodeURIComponent(token)}` : '';
  });

  const {
    status: wsStatus,
    data: wsData,
    send: wsSend,
    open: wsOpen,
    close: wsClose
  } = useWebSocket(wsUrl, {
    autoReconnect: true,
    immediate: computed(() => Boolean(authStore.token?.trim()))
  });

  function isActiveChat() {
    return list.value.some(m => m.status === 'pending' || m.status === 'loading');
  }

  const scrollToBottom = ref<null | (() => void)>(null);

  const previewVisible = ref(false);
  const previewFileName = ref('');

  function openFilePreview(fileName: string) {
    previewFileName.value = fileName;
    previewVisible.value = true;
  }

  function closeFilePreview() {
    previewVisible.value = false;
    previewFileName.value = '';
  }

  return {
    input,
    conversationId,
    list,
    wsStatus,
    wsData,
    wsSend,
    wsOpen,
    wsClose,
    scrollToBottom,
    previewVisible,
    previewFileName,
    openFilePreview,
    closeFilePreview,
    isActiveChat
  };
});
