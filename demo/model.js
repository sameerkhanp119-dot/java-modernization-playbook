export function price(cents,member,region,regression=false){
  if(!Number.isSafeInteger(cents)||cents<0||cents>100000000)throw new Error('Enter an amount from $0 to $1,000,000 with at most two decimals.');
  if(!['US','EU'].includes(region))throw new Error('Unsupported region.');
  const discount=member && cents>=10000?Math.min(3000,Math.floor((cents*75+500)/1000)):0;
  const net=cents-discount;
  // Deliberate regression uses the pre-discount shipping boundary.
  const shipping=(regression?cents:net)>=15000?0:region==='US'?795:1495;
  return {discount,shipping,total:net+shipping};
}
export async function bucket(key){
  if(!key.trim())throw new Error('A stable routing key is required.');
  const hash=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(key));
  return new DataView(hash).getUint32(0)%100;
}
export function route({mode,percent,bucket:cohort,cents,member,region,regression=false,failure=false}){
  const legacy=price(cents,member,region),modern=price(cents,member,region,regression);
  const compared=mode==='SHADOW'&&!failure;
  const mismatch=compared && JSON.stringify(legacy)!==JSON.stringify(modern);
  const candidate=mode==='MODERN'||(mode==='CANARY' && cohort<percent);
  const fallback=candidate&&failure;
  return {legacy,modern:failure?null:modern,result:candidate&&!failure?modern:legacy,
    servedBy:candidate&&!failure?'modern':'legacy',compared,mismatch,fallback,failed:failure&&(candidate||mode==='SHADOW')};
}
