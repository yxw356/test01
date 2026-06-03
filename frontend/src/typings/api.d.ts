/**
 * Namespace Api
 *
 * All backend api type
 */
declare namespace Api {
  namespace Common {
    /** common params of paginating */
    interface PaginatingCommonParams {
      /** current page number */
      page?: number;
      number: number;
      /** page size */
      size?: number;
      /** total count */
      totalElements: number;
    }

    /** common params of paginating query list data */
    interface PaginatingQueryRecord<T = any> extends PaginatingCommonParams {
      data: T[];
      content: T[];
    }

    /** common search params of table */
    type CommonSearchParams = Pick<Common.PaginatingCommonParams, 'page' | 'size'>;
  }

  /**
   * namespace Auth
   *
   * backend api module: "auth"
   */
  namespace Auth {
    interface LoginToken {
      token: string;
      refreshToken: string;
    }

    interface UserInfo {
      id: number;
      username: string;
      role: 'USER' | 'DEPT_MEMBER' | 'DEPT_LEAD' | 'KNOWLEDGE_ADMIN' | 'SUPER_ADMIN' | 'ADMIN';
      orgTags: string[];
      primaryOrg: string;
    }
  }

  /**
   * namespace Route
   *
   * backend api module: "route"
   */
  namespace Route {
    type ElegantConstRoute = import('@elegant-router/types').ElegantConstRoute;

    interface MenuRoute extends ElegantConstRoute {
      id: string;
    }

    interface UserRoute {
      routes: MenuRoute[];
      home: import('@elegant-router/types').LastLevelRouteKey;
    }
  }

  namespace OrgTag {
    interface Item {
      tagId: string;
      name: string;
      description: string;
      parentTag: string | null;
      children?: Item[];
      deptLeads?: string[];
    }

    type List = Common.PaginatingQueryRecord<Item>;

    type Details = Pick<Item, 'tagId' | 'name' | 'description'>;
    type Mine = {
      orgTags: string[];
      primaryOrg: string;
      orgTagDetails: Details[];
    };
  }

  namespace User {
    type SearchParams = CommonType.RecordNullable<
      Common.CommonSearchParams & {
        keyword: string;
        orgTag: string;
        role: Auth.UserInfo['role'];
        status: number;
      }
    >;

    type Item = {
      userId: string;
      username: string;
      role: Auth.UserInfo['role'];
      email: string;
      status: number;
      orgTags: Pick<OrgTag.Item, 'tagId' | 'name'>[];
      primaryOrg: string;
      createTime: string;
      createdAt?: string;
      updatedAt?: string;
      lastLoginTime: string;
    };

    type List = Common.PaginatingQueryRecord<Item>;
  }

  namespace KnowledgeBase {
    interface SearchParams {
      userId: string;
      query: string;
      topK: number;
    }

    interface SearchResult {
      fileMd5: string;
      chunkId: number;
      textContent: string;
      score: number;
      fileName: string;
    }

    interface UploadState {
      tasks: UploadTask[];
      activeUploads: Set<string>; // 当前正在上传的任务ID
    }

    interface Form {
      orgTag: string | null;
      orgTagName: string | null;
      knowledgeScope: 'PUBLIC' | 'DEPARTMENT' | 'PRIVATE';
      departmentId: string | null;
      categoryId: number | null;
      categoryName: string | null;
      cleaningRuleSetId: number | null;
      isPublic: boolean;
      fileList: import('naive-ui').UploadFileInfo[];
    }

    interface Category {
      id: number;
      name: string;
      parentId?: number | null;
      knowledgeScope: 'PUBLIC' | 'DEPARTMENT' | 'PRIVATE';
      departmentId?: string | null;
      description?: string | null;
      sortOrder: number;
      enabled: boolean;
    }

    interface CategoryCreateForm {
      name: string;
      parentId?: number | null;
      knowledgeScope: 'PUBLIC' | 'DEPARTMENT' | 'PRIVATE';
      departmentId?: string | null;
      description?: string | null;
      sortOrder?: number;
    }

    interface CleaningRuleConfig {
      normalizeLineBreaks: boolean;
      normalizeUnicodeSpaces: boolean;
      normalizeWhitespace: boolean;
      trimLines: boolean;
      collapseBlankLines: boolean;
      removeDuplicateLines: boolean;
      minDuplicateLineLength: number;
      dropLinePatterns: string[];
    }

    interface CleaningRuleSet extends CleaningRuleConfig {
      id: number;
      name: string;
      knowledgeScope: 'PUBLIC' | 'DEPARTMENT' | 'PRIVATE';
      departmentId?: string | null;
      description?: string | null;
      enabled: boolean;
      createdBy?: string | null;
      createdAt?: string;
      updatedAt?: string;
    }

    interface CleaningRuleSetCreateForm extends CleaningRuleConfig {
      name: string;
      knowledgeScope: 'PUBLIC' | 'DEPARTMENT' | 'PRIVATE';
      departmentId: string | null;
      description: string | null;
    }

    interface CleaningPreviewRequest {
      rawText: string;
      ruleConfig?: CleaningRuleConfig | null;
      ruleSetId?: number | null;
    }

    interface CleaningPreviewResult {
      cleanedText: string;
      originalChars: number;
      cleanedChars: number;
      removedChars: number;
      duplicateLinesRemoved: number;
      compressionRatio: number;
    }

    interface UploadTask {
      file?: File;
      chunk: Blob | null;
      fileMd5: string;
      chunkIndex: number;
      totalSize: number;
      fileName: string;
      userId?: string;
      orgTag: string | null;
      orgTagName?: string | null;
      knowledgeScope?: 'PUBLIC' | 'DEPARTMENT' | 'PRIVATE';
      departmentId?: string | null;
      categoryId?: number | null;
      categoryName?: string | null;
      cleaningRuleSetId?: number | null;
      cleaningStatus?: 'PENDING' | 'CLEANING' | 'CLEANED' | 'FAILED';
      originalChars?: number;
      cleanedChars?: number;
      removedChars?: number;
      duplicateLinesRemoved?: number;
      public: boolean;
      isPublic: boolean;
      canView?: boolean;
      canManage?: boolean;
      uploadedChunks: number[];
      progress: number;
      status: UploadStatus;
      createdAt?: string;
      mergedAt?: string;
      indexStatus?: number;
      indexError?: string | null;
      uploadError?: string;
      requestIds?: string[]; // 请求ID，用于取消上传
    }
    type List = Common.PaginatingQueryRecord<UploadTask>;

    type Merge = Pick<UploadTask, 'fileMd5' | 'fileName' | 'cleaningRuleSetId'>;

    interface Progress {
      uploaded: number[];
      progress: number;
      totalChunks: number;
    }

    interface UploadPreflight {
      ready: boolean;
      message: string;
      components: Record<string, { status: string; detail?: string; bucket?: string; topic?: string }>;
      uploadLimits?: {
        maxFileSize: number;
        maxFileSizeLabel: string;
      };
    }

    interface Result {
      objectUrl: string;
      fileSize: number;
    }
  }

  namespace Chat {
    interface Input {
      message: string;
      conversationId?: string;
    }

    interface Output {
      chunk: string;
    }

    interface Conversation {
      conversationId: string;
    }

    interface Message {
      role: 'user' | 'assistant';
      content: string;
      status?: 'pending' | 'loading' | 'finished' | 'error';
      timestamp?: string;
      citations?: RetrievalCitation[];
    }

    interface RetrievalCitation {
      index: number;
      fileMd5?: string;
      fileName?: string;
      chunkId?: number;
      parentId?: string;
      score?: number;
      snippet?: string;
    }

    interface Token {
      cmdToken: string;
    }
  }

  namespace Document {
    interface DownloadResponse {
      fileName: string;
      downloadUrl: string;
      fileSize: number;
    }
  }

  namespace Admin {
    interface AuditLog {
      id: number;
      userId?: string;
      username?: string;
      action: string;
      resourceType?: string;
      resourceId?: string;
      detail?: string;
      result?: string;
      clientIp?: string;
      durationMs?: number;
      createdAt: string;
    }

    interface ComponentStatus {
      status?: string;
      detail?: string;
      knowledgeBaseCount?: number;
      totalLag?: number;
      clusterStatus?: string;
    }

    interface MonitoringStatus {
      timestamp?: string;
      components?: {
        redis?: ComponentStatus;
        minio?: ComponentStatus;
        elasticsearch?: ComponentStatus;
        vllmChat?: ComponentStatus;
        vllmEmbedding?: ComponentStatus;
        kafka?: ComponentStatus;
      };
      metrics?: {
        indexSuccessCount?: number;
        indexFailureCount?: number;
        lastIndexFailureMessage?: string;
        lastIndexFailureAt?: string;
        chatRequestCount?: number;
        chatAverageDurationMs?: number;
        chatP95EstimateMs?: number;
      };
    }
  }
}
