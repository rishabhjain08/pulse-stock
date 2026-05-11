'use strict';

const { CloudWatchLogsClient, DescribeLogStreamsCommand, GetLogEventsCommand } = require('@aws-sdk/client-cloudwatch-logs');
const { loadEnv, cfg } = require('./_common');

(async () => {
  loadEnv();
  const logs = new CloudWatchLogsClient(cfg());

  const logGroupName = '/aws/lambda/poarvault-link-token';
  
  // 1. Get the latest log stream
  const { logStreams } = await logs.send(new DescribeLogStreamsCommand({
    logGroupName,
    orderBy: 'LastEventTime',
    descending: true,
    limit: 1
  }));

  if (!logStreams || logStreams.length === 0) {
    console.log('No log streams found.');
    return;
  }

  const logStreamName = logStreams[0].logStreamName;
  console.log(`==> Reading stream: ${logStreamName}\n`);

  // 2. Get events from that stream
  const { events } = await logs.send(new GetLogEventsCommand({
    logGroupName,
    logStreamName,
    limit: 50
  }));

  for (const event of events) {
    const date = new Date(event.timestamp).toISOString();
    process.stdout.write(`[${date}] ${event.message}`);
  }

})().catch(e => { console.error(e.message); process.exit(1); });
