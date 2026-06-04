import assert from 'node:assert/strict';
import { buildKnowledgeSpaces, filterTasksBySpace, applySpaceLayout } from './knowledge-space';

type Task = Parameters<typeof buildKnowledgeSpaces>[0][number];

function task(input: Partial<Task> & Pick<Task, 'fileMd5' | 'fileName'>): Task {
  return {
    fileMd5: input.fileMd5,
    fileName: input.fileName,
    status: input.status ?? 1,
    public: input.public,
    isPublic: input.isPublic,
    knowledgeScope: input.knowledgeScope,
    departmentId: input.departmentId,
    orgTag: input.orgTag,
    orgTagName: input.orgTagName,
    indexStatus: input.indexStatus,
    cleaningStatus: input.cleaningStatus,
    mergedAt: input.mergedAt,
    createdAt: input.createdAt
  } as Task;
}

const tasks = [
  task({ fileMd5: 'p1', fileName: '公共制度.md', knowledgeScope: 'PUBLIC', isPublic: true, indexStatus: 2 }),
  task({ fileMd5: 'h1', fileName: '人事手册.md', knowledgeScope: 'DEPARTMENT', departmentId: 'HR', orgTagName: '人事部' }),
  task({ fileMd5: 'h2', fileName: '人事流程.md', knowledgeScope: 'DEPARTMENT', departmentId: 'HR', orgTagName: '人事部' }),
  task({ fileMd5: 'f1', fileName: '财务制度.md', knowledgeScope: 'DEPARTMENT', departmentId: 'FIN', orgTagName: '财务部' })
];

const spaces = buildKnowledgeSpaces(tasks);

assert.deepEqual(
  spaces.map(item => ({ id: item.id, title: item.title, fileCount: item.fileCount })),
  [
    { id: 'PUBLIC', title: '公共知识库', fileCount: 1 },
    { id: 'DEPARTMENT:HR', title: '人事部知识库', fileCount: 2 },
    { id: 'DEPARTMENT:FIN', title: '财务部知识库', fileCount: 1 }
  ]
);

assert.equal(filterTasksBySpace(tasks, 'PUBLIC').length, 1);
assert.deepEqual(
  filterTasksBySpace(tasks, 'DEPARTMENT:HR').map(item => item.fileMd5),
  ['h1', 'h2']
);

assert.deepEqual(
  applySpaceLayout(spaces, ['DEPARTMENT:FIN', 'PUBLIC']).map(item => item.id),
  ['DEPARTMENT:FIN', 'PUBLIC', 'DEPARTMENT:HR']
);

console.log('knowledge-space tests passed');
