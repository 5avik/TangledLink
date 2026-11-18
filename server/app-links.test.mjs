import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
const script=readFileSync(new URL('./app-links/return.js',import.meta.url),'utf8');
test('fallback opens only the fixed application destination, never browser query parameters',()=>{
 const destinations=[],history=[];
 const window={location:{replace:url=>destinations.push(url)}};
 Object.defineProperty(window.location,'search',{get:()=>{throw new Error('Untrusted query must not be read')}});
 vm.runInNewContext(script,{window,navigator:{userAgent:'Android Chrome'},history:{replaceState:(...args)=>history.push(args)}});
 assert.deepEqual(destinations,['intent://billing/return#Intent;scheme=tangledlink;package=pro.tngl;end']);
 assert.equal(history[0][2],'/billing/return');
});
test('desktop fallback never attempts an Android launch',()=>{
 const destinations=[];
 vm.runInNewContext(script,{window:{location:{replace:url=>destinations.push(url)}},navigator:{userAgent:'Desktop'},history:{replaceState:()=>{}}});
 assert.deepEqual(destinations,[]);
});
test('asset links authorize only the app and well-formed certificate fingerprints',()=>{
 const links=JSON.parse(readFileSync(new URL('./app-links/assetlinks.json',import.meta.url),'utf8'));
 assert.equal(links.length,1);
 assert.equal(links[0].target.package_name,'pro.tngl');
 assert.deepEqual(links[0].relation,['delegate_permission/common.handle_all_urls']);
 assert.ok(links[0].target.sha256_cert_fingerprints.every(x=>/^(?:[A-F0-9]{2}:){31}[A-F0-9]{2}$/.test(x)));
});
