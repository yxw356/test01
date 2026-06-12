<script setup lang="ts">
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import { nextTick } from 'vue';
import { VueMarkdownIt } from 'vue-markdown-shiki';
import { formatDate } from '@/utils/common';
defineOptions({ name: 'ChatMessage' });

const props = defineProps<{ msg: Api.Chat.Message }>();

const authStore = useAuthStore();

function handleCopy(content: string) {
  navigator.clipboard.writeText(content);
  window.$message?.success('已复制');
}

const chatStore = useChatStore();

const citationsExpanded = ref(false);

// 存储文件名和对应的事件处理
const sourceFiles = ref<Array<{fileName: string, id: string}>>([]);

// 处理来源文件链接的函数
function processSourceLinks(text: string): string {
  sourceFiles.value = [];
  // 匹配 (来源#数字: 文件名) 的正则表达式
  const sourcePattern = /\(来源#(\d+):\s*([^)]+)\)/g;

  return text.replace(sourcePattern, (_match, sourceNum, fileName) => {
    // 为文件名创建可点击的链接
    const linkClass = 'source-file-link';
    const encodedFileName = encodeURIComponent(fileName.trim());
    const fileId = `source-file-${sourceFiles.value.length}`;

    // 存储文件信息
    sourceFiles.value.push({
      fileName: encodedFileName,
      id: fileId
    });

    return `(来源#${sourceNum}: <span class="${linkClass}" data-file-id="${fileId}">${fileName}</span>)`;
  });
}

const content = computed(() => {
  chatStore.scrollToBottom?.();
  const rawContent = props.msg.content ?? '';

  // 只对助手消息处理来源链接
  if (props.msg.role === 'assistant') {
    if (!rawContent.trim() && props.msg.status !== 'pending') {
      return '回答内容未保存，请重新提问。';
    }

    return processSourceLinks(rawContent);
  }

  return rawContent;
});

const visibleCitations = computed(() => {
  if (!props.msg.citations?.length) return [];
  return citationsExpanded.value ? props.msg.citations.slice(0, 5) : [];
});

// 处理内容点击事件（事件委托）
function handleContentClick(event: MouseEvent) {
  const target = event.target as HTMLElement;

  // 检查点击的是否是文件链接
  if (target.classList.contains('source-file-link')) {
    const fileId = target.getAttribute('data-file-id');
    if (fileId) {
      const file = sourceFiles.value.find(f => f.id === fileId);
      if (file) {
        handleSourceFileClick(file.fileName);
      }
    }
  }
}

function openFilePreview(fileName: string) {
  chatStore.openFilePreview(decodeURIComponent(fileName));
}

async function handleSourceFileClick(fileName: string) {
  openFilePreview(fileName);
}

function openCitation(citation: Api.Chat.RetrievalCitation) {
  const params = parseCitationUrlParams(citation.previewUrl);
  const fileMd5 = params.fileMd5 || citation.fileMd5 || '';
  const fileName = params.fileName || citationDisplayName(citation);
  if (!fileName && !fileMd5) return;
  chatStore.openFilePreview(fileName, fileMd5);
}

function citationDisplayName(citation: Api.Chat.RetrievalCitation) {
  if (citation.fileName?.trim()) return citation.fileName.trim();
  const inferred = inferFileNameFromSnippet(citation.snippet || '');
  return inferred || '知识库文件';
}

function inferFileNameFromSnippet(snippet: string) {
  if (!snippet) return '';
  const fileNameMatch = snippet.match(
    /(?:文件名称|文件名|制度名称)[:：]\s*(.{2,80}?)(?=文件编号|生效日期|状态|文件版本|页数|版号|编制人|审核人|批准人|\s|。|；|;|，|,|《|》|$)/
  );
  if (fileNameMatch?.[1]) return sanitizeCitationName(fileNameMatch[1]);
  const bookTitleMatch = snippet.match(/《([^》]{2,80})》/);
  if (bookTitleMatch?.[1]) return sanitizeCitationName(bookTitleMatch[1]);
  return '';
}

function sanitizeCitationName(name: string) {
  return name.replace(/[\s　]+/g, '').replace(/^(公司|江西龙汇肉制品有限责任公司)/, '').trim();
}

function parseCitationUrlParams(url?: string) {
  if (!url) return { fileName: '', fileMd5: '' };
  try {
    const parsed = new URL(url, window.location.origin);
    return {
      fileName: parsed.searchParams.get('fileName') || '',
      fileMd5: parsed.searchParams.get('fileMd5') || ''
    };
  } catch {
    return { fileName: '', fileMd5: '' };
  }
}
</script>

<template>
  <div class="chat-message mb-7">
    <div :class="['message-row', msg.role === 'user' ? 'message-row-user' : 'message-row-assistant']">
      <NAvatar v-if="msg.role !== 'user'" class="assistant-avatar message-avatar">
        <SystemLogo class="text-6" />
      </NAvatar>

      <div :class="['message-body', msg.role === 'user' ? 'message-body-user' : 'message-body-assistant']">
        <div class="message-meta">
          <NText class="text-4 font-bold">{{ msg.role === 'user' ? authStore.userInfo.username : '龙汇QA' }}</NText>
          <NText class="text-3 color-gray-500">{{ formatDate(msg.timestamp) }}</NText>
        </div>

        <NText v-if="msg.status === 'pending'" class="message-pending">
          <icon-eos-icons:three-dots-loading class="text-8" />
        </NText>
        <NText v-else-if="msg.status === 'error'" class="message-error italic">服务器繁忙，请稍后再试</NText>
        <div v-else-if="msg.role === 'assistant'" class="assistant-message-shell" @click="handleContentClick">
          <div class="assistant-message-card">
            <VueMarkdownIt :content="content" />
          </div>
          <div v-if="msg.citations?.length" class="citations-panel mt-3">
            <NButton size="small" quaternary class="citations-toggle" @click.stop="citationsExpanded = !citationsExpanded">
              <template #icon>
                <icon-mdi-chevron-down v-if="citationsExpanded" />
                <icon-mdi-chevron-right v-else />
              </template>
              参考来源（{{ msg.citations.length }}）
            </NButton>
            <div
              v-for="citation in visibleCitations"
              :key="citation.index"
              class="citation-item"
              role="button"
              tabindex="0"
              @click.stop="openCitation(citation)"
              @keydown.enter.stop.prevent="openCitation(citation)"
            >
              <div class="citation-title">
                <span class="citation-index">#{{ citation.index }}</span>
                <span class="citation-name">{{ citationDisplayName(citation) }}</span>
                <NTag v-if="citation.chunkId != null" size="small" :bordered="false">
                  片段 {{ citation.chunkId }}
                </NTag>
                <NTag v-if="citation.score != null" size="small" :bordered="false" type="info">
                  {{ citation.score.toFixed(3) }}
                </NTag>
                <NButton size="tiny" text type="primary" class="citation-open">
                  打开
                  <template #icon>
                    <icon-mdi-open-in-new />
                  </template>
                </NButton>
              </div>
              <p v-if="citation.snippet" class="citation-snippet">{{ citation.snippet }}</p>
            </div>
            <NText v-if="citationsExpanded && msg.citations.length > visibleCitations.length" depth="3" class="text-12px">
              已收起其余 {{ msg.citations.length - visibleCitations.length }} 条来源
            </NText>
          </div>
        </div>
        <div v-else-if="msg.role === 'user'" class="user-message-card text-4">{{ content }}</div>

        <div class="message-actions">
          <NButton quaternary circle size="small" @click="handleCopy(msg.content)">
            <template #icon>
              <icon-mynaui:copy />
            </template>
          </NButton>
        </div>
      </div>

      <NAvatar v-if="msg.role === 'user'" class="user-avatar message-avatar">
        <SvgIcon icon="ph:user-circle" class="text-icon-large color-white" />
      </NAvatar>
    </div>
  </div>
</template>

<style scoped lang="scss">
.user-avatar {
  background: rgb(var(--success-color));
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: 100%;
}

.message-row-user {
  justify-content: flex-end;
}

.message-row-assistant {
  justify-content: flex-start;
}

.message-avatar {
  flex: 0 0 auto;
}

.message-body {
  display: flex;
  flex-direction: column;
  max-width: min(78%, 60rem);
}

.message-body-user {
  align-items: flex-end;
}

.message-body-assistant {
  align-items: flex-start;
}

.message-meta {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
}

.message-body-user .message-meta {
  flex-direction: row-reverse;
}

.message-actions {
  display: flex;
  margin-top: 6px;
  opacity: 0.66;
}

.message-body-user .message-actions {
  justify-content: flex-end;
}

.message-body-assistant .message-actions {
  justify-content: flex-start;
}

.message-pending,
.message-error {
  margin-top: 4px;
}

:deep(.assistant-avatar) {
  color: rgb(var(--primary-color));
  background: rgb(var(--primary-color) / 0.08);
  border: 1px solid rgb(var(--primary-color) / 0.14);
}

.assistant-message-shell {
  width: 100%;
}

:deep(.assistant-message-card) {
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  background: rgb(248 250 252);
  padding: 16px 18px;
}

.user-message-card {
  border: 1px solid rgb(var(--primary-color) / 0.1);
  border-radius: 8px;
  background: rgb(var(--primary-color));
  color: #fff;
  padding: 12px 14px;
  line-height: 1.75;
  white-space: pre-wrap;
  text-align: left;
}

:deep(.assistant-message-card > :first-child) {
  margin-top: 0;
}

:deep(.assistant-message-card > :last-child) {
  margin-bottom: 0;
}

:deep(.assistant-message-card p) {
  margin: 0 0 1em;
  line-height: 1.9;
}

:deep(.assistant-message-card ul),
:deep(.assistant-message-card ol) {
  padding-left: 1.25rem;
  line-height: 1.85;
}

:deep(.assistant-message-card li + li) {
  margin-top: 0.35rem;
}

:deep(.assistant-message-card a) {
  color: rgb(var(--primary-color));
}

:deep(.assistant-message-card code) {
  border-radius: 6px;
  background: rgb(var(--primary-color) / 0.06);
  padding: 0.1em 0.38em;
}

:deep(.assistant-message-card pre) {
  overflow-x: auto;
  border: 1px solid rgb(var(--primary-color) / 0.08);
  border-radius: 8px;
  background: rgb(var(--container-bg-color)) !important;
  margin: 1em 0;
}

:deep(.assistant-message-card pre code) {
  background: transparent;
  padding: 0;
}

:deep(.assistant-message-card blockquote) {
  border-left: 3px solid rgb(var(--info-color) / 0.58);
  border-radius: 0 8px 8px 0;
  background: rgb(var(--info-color) / 0.06);
  margin: 1em 0;
  padding: 10px 14px;
}

:deep(.assistant-message-card hr) {
  border: 0;
  border-top: 1px solid rgb(var(--primary-color) / 0.08);
  margin: 1.2em 0;
}

:deep(.source-file-link) {
  color: rgb(var(--primary-color));
  cursor: pointer;
  font-weight: 600;
  text-decoration: none;
  transition: color 0.2s;

  &:hover {
    color: rgb(var(--primary-color));
    text-decoration: none;
  }

  &:active {
    color: rgb(var(--primary-700-color));
  }
}

.citations-panel {
  display: grid;
  gap: 8px;
  max-width: min(100%, 60rem);
}

.citations-toggle {
  justify-self: start;
  padding-left: 0;
}

.citation-item {
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  background: rgb(var(--container-bg-color));
  padding: 10px 12px;
  cursor: pointer;
  transition: border-color 0.2s;

  &:hover {
    border-color: rgb(var(--primary-color) / 0.35);
  }

  &:focus-visible {
    outline: 2px solid rgb(var(--primary-color) / 0.45);
    outline-offset: 2px;
  }
}

.citation-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.citation-index {
  color: rgb(var(--primary-color));
  font-weight: 600;
  font-size: 13px;
}

.citation-name {
  font-size: 13px;
  font-weight: 500;
}

.citation-open {
  margin-left: auto;
}

.citation-snippet {
  margin: 8px 0 0;
  color: rgb(var(--base-text-color) / 0.62);
  font-size: 12px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

:deep(.message-divider.n-divider) {
  --n-color: rgb(15 23 42 / 0.06);
}

html.dark :deep(.assistant-avatar) {
  border-color: rgb(var(--primary-color) / 0.2);
}

html.dark :deep(.assistant-message-card) {
  border-color: rgb(255 255 255 / 0.08);
  background: rgb(15 23 42);
}

html.dark .user-message-card {
  border-color: rgb(var(--primary-color) / 0.22);
  background: rgb(var(--primary-color) / 0.12);
}

html.dark :deep(.assistant-message-card code) {
  background: rgb(var(--primary-color) / 0.12);
}

html.dark :deep(.assistant-message-card pre) {
  border-color: rgb(255 255 255 / 0.08);
  background: rgb(2 6 23) !important;
}

html.dark :deep(.assistant-message-card blockquote) {
  border-left-color: rgb(var(--info-color) / 0.5);
  background: rgb(var(--info-color) / 0.12);
}

html.dark :deep(.assistant-message-card hr) {
  border-top-color: rgb(255 255 255 / 0.08);
}

html.dark :deep(.message-divider.n-divider) {
  --n-color: rgb(255 255 255 / 0.08);
}
</style>
