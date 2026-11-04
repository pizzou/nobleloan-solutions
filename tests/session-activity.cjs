// Run: TYPESCRIPT_PATH=/path/to/typescript/lib/typescript.js node tests/session-activity.cjs
const ts = require(process.env.TYPESCRIPT_PATH || 'typescript');
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const source = fs.readFileSync(path.join(root, 'frontend/loan-management-ui/hooks/useSessionActivity.ts'), 'utf8');
const js = ts.transpileModule(source, {compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText;
function setup() {
 let now=1000000, activity=now, renewals=0, logouts=0, resolveRenew;
 const events={}, effects=[]; let tick;
 const ctx={exports:{}, Date:{now:()=>now}, document:{visibilityState:'visible',addEventListener(){},removeEventListener(){}},window:{addEventListener:(n,f)=>events[n]=f,removeEventListener(){},setInterval:f=>{tick=f;return 1},clearInterval(){}},require:n=>n==='react'?{useEffect:f=>effects.push(f),useRef:x=>({current:x})}:n==='@/services/api'?{authApi:{renew:()=>{renewals++; return new Promise(r=>resolveRenew=r)}}}:{SESSION_ACTIVITY_KEY:'activity',SESSION_IDLE_MS:600000,lastSessionActivity:()=>activity,noteSessionActivity:t=>activity=t??now}};
 vm.runInNewContext(js,ctx);ctx.exports.useSessionActivity(true,()=>logouts++); effects.forEach(f=>f());
 return {advance(ms){now+=ms;tick()},act(){events.pointerdown()},scroll(){events.scroll()},resolve:async()=>{resolveRenew();await Promise.resolve();await Promise.resolve();await Promise.resolve()},counts:()=>({renewals,logouts})};
}
(async()=>{
 let s=setup();await s.resolve();s.advance(599999);assert.equal(s.counts().logouts,0);s.advance(1);assert.equal(s.counts().logouts,1);
 s=setup();s.advance(20000);s.act();await s.resolve();s.advance(30000);assert.equal(s.counts().renewals,2,'activity during pending renewal must receive a new renewal');await s.resolve();
 for(let i=0;i<30;i++){s.advance(60000);s.scroll();await s.resolve()}
 assert.equal(s.counts().logouts,0,'active user remains signed in beyond ten minutes');
 s.advance(600000);assert.equal(s.counts().logouts,1);
 console.log('PASS: exact idle boundary, pending-renewal activity, 30 minutes active, subsequent idle logout');
})().catch(e=>{console.error(e);process.exitCode=1});
