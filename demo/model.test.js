import test from 'node:test';import assert from 'node:assert/strict';import {price,bucket,route} from './model.js';
test('integer-cent model preserves discount rounding and discounted shipping threshold',()=>{
  assert.deepEqual(price(15000,true,'US'),{discount:1125,shipping:795,total:14670});
  assert.deepEqual(price(10027,true,'EU'),{discount:752,shipping:1495,total:10770});
  assert.deepEqual(price(100000,true,'US'),{discount:3000,shipping:0,total:97000});
  assert.throws(()=>price(1.01,true,'US'));assert.throws(()=>price(-1,true,'US'));
});
test('shadow mismatch serves legacy while canary candidate can expose regression',()=>{
  const args={mode:'SHADOW',percent:100,bucket:0,cents:15000,member:true,region:'US',regression:true};
  const shadow=route(args);assert.equal(shadow.mismatch,true);assert.equal(shadow.result.total,14670);
  const modern=route({...args,mode:'CANARY'});assert.equal(modern.servedBy,'modern');assert.equal(modern.result.total,13875);
  assert.equal(route({...args,mode:'MODERN',failure:true}).fallback,true);
  assert.equal(route({...args,mode:'LEGACY',failure:true}).failed,false);
});
test('SHA-256 bucket matches the documented first-four-byte unsigned algorithm',async()=>{
  assert.equal(await bucket('abc'),19);assert.equal(await bucket('abc'),await bucket('abc'));await assert.rejects(()=>bucket(' '));
});
