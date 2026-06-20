export type KnowledgeSpaceTask = Pick<
  Api.KnowledgeBase.UploadTask,
  | 'fileMd5'
  | 'fileName'
  | 'status'
  | 'public'
  | 'isPublic'
  | 'knowledgeScope'
  | 'departmentId'
  | 'orgTag'
  | 'orgTagName'
  | 'indexStatus'
  | 'cleaningStatus'
  | 'createdAt'
  | 'mergedAt'
>;

export type KnowledgeSpaceType = 'PUBLIC' | 'DEPARTMENT' | 'PRIVATE';

export const ACTIVE_KNOWLEDGE_SPACE_KEY = 'active-knowledge-space-context';

export interface KnowledgeSpace {
  id: string;
  type: KnowledgeSpaceType;
  title: string;
  departmentId: string | null;
  fileCount: number;
  indexedCount: number;
  processingCount: number;
  interruptedCount: number;
  cleaningIssueCount: number;
  lastUpdatedAt: string | null;
}

export function normalizeTaskScope(task: KnowledgeSpaceTask): KnowledgeSpaceType {
  return (task.knowledgeScope as KnowledgeSpaceType) || (task.public || task.isPublic ? 'PUBLIC' : 'DEPARTMENT');
}

export function taskDepartmentId(task: KnowledgeSpaceTask) {
  return task.departmentId || task.orgTag || null;
}

export function spaceIdForTask(task: KnowledgeSpaceTask) {
  const scope = normalizeTaskScope(task);
  if (scope === 'PUBLIC') return 'PUBLIC';
  if (scope === 'PRIVATE') return 'PRIVATE';
  return `DEPARTMENT:${taskDepartmentId(task) || 'UNKNOWN'}`;
}

export function buildKnowledgeSpaces(tasks: KnowledgeSpaceTask[]): KnowledgeSpace[] {
  const groups = new Map<string, KnowledgeSpaceTask[]>();
  groups.set('PUBLIC', []);

  for (const task of tasks) {
    const spaceId = spaceIdForTask(task);
    const group = groups.get(spaceId) || [];
    group.push(task);
    groups.set(spaceId, group);
  }

  return Array.from(groups.entries())
    .filter(([id, group]) => id === 'PUBLIC' || group.length > 0)
    .map(([id, group]) => createKnowledgeSpace(id, group));
}

export function filterTasksBySpace(tasks: KnowledgeSpaceTask[], selectedSpaceId: string | null) {
  if (!selectedSpaceId) return tasks;
  return tasks.filter(task => spaceIdForTask(task) === selectedSpaceId);
}

export function applySpaceLayout(spaces: KnowledgeSpace[], orderedIds: string[]) {
  const spaceMap = new Map(spaces.map(space => [space.id, space]));
  const ordered = orderedIds.map(id => spaceMap.get(id)).filter(Boolean) as KnowledgeSpace[];
  const orderedSet = new Set(ordered.map(space => space.id));
  return [...ordered, ...spaces.filter(space => !orderedSet.has(space.id))];
}

function createKnowledgeSpace(id: string, tasks: KnowledgeSpaceTask[]): KnowledgeSpace {
  const firstTask = tasks[0];
  const type = id === 'PUBLIC' ? 'PUBLIC' : id.startsWith('PRIVATE') ? 'PRIVATE' : 'DEPARTMENT';
  const departmentId = type === 'DEPARTMENT' ? id.replace('DEPARTMENT:', '') : null;
  const departmentName = firstTask?.orgTagName || departmentId;

  return {
    id,
    type,
    title: type === 'PUBLIC' ? '公共知识库' : type === 'PRIVATE' ? '个人知识库' : `${departmentName || '未归属部门'}知识库`,
    departmentId,
    fileCount: tasks.length,
    indexedCount: tasks.filter(task => !task.indexStatus || task.indexStatus === 2).length,
    processingCount: tasks.filter(task => task.status !== 1).length,
    interruptedCount: tasks.filter(task => task.status === 3).length,
    cleaningIssueCount: tasks.filter(task => ['FAILED', 'LOW_QUALITY', 'WARNING'].includes(String(task.cleaningStatus))).length,
    lastUpdatedAt: latestTime(tasks)
  };
}

function latestTime(tasks: KnowledgeSpaceTask[]) {
  return (
    tasks
      .map(task => task.mergedAt || task.createdAt)
      .filter((value): value is string => Boolean(value))
      .sort()
      .at(-1) || null
  );
}
