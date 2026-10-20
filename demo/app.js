import {bucket,route} from './model.js';
const $=id=>document.getElementById(id),money=cents=>new Intl.NumberFormat('en-US',{style:'currency',currency:'USD'}).format(cents/100);
let mode='SHADOW',generation=0;
const explanations={LEGACY:'Legacy serves · candidate bypassed',SHADOW:'Legacy serves · candidate compares',CANARY:'Stable cohort selects authority',MODERN:'Candidate serves · legacy fallback available'};
for(const button of document.querySelectorAll('[data-mode]'))button.addEventListener('click',()=>{mode=button.dataset.mode;syncMode();evaluate();});
function syncMode(){for(const b of document.querySelectorAll('[data-mode]')){b.classList.toggle('selected',b.dataset.mode===mode);b.setAttribute('aria-pressed',String(b.dataset.mode===mode));}$('mode-badge').textContent=mode+' MODE';$('routing-display').textContent=explanations[mode];}
async function evaluate(){
  const id=++generation;$('error').hidden=true;
  try{
    const amount=$('amount').value;if(!/^(\d+)(\.\d{1,2})?$/.test(amount))throw new Error('Enter a nonnegative amount with at most two decimal places.');
    const [whole,fraction='']=amount.split('.'),cents=Number(whole)*100+Number(fraction.padEnd(2,'0'));
    const key=$('routing-key').value,cohort=await bucket(key);if(id!==generation)return;
    const percent=Number($('percent').value),result=route({mode,percent,bucket:cohort,cents,member:$('member').checked,region:$('region').value,regression:$('regression').checked,failure:$('failure').checked});
    $('key-display').textContent=key;$('percent-label').textContent=percent+'%';$('bucket-label').textContent=`Bucket ${cohort} / 99`;
    const candidate=mode==='MODERN'||mode==='CANARY'&&cohort<percent;
    $('legacy-path').classList.toggle('authority',result.servedBy==='legacy');$('modern-path').classList.toggle('authority',result.servedBy==='modern');
    const display=(prefix,value,used)=>{ $(prefix+'-total').textContent=value?money(value.total):'Exception';$(prefix+'-breakdown').textContent=used?(value?`Discount ${money(value.discount)} · Shipping ${money(value.shipping)}`:'Injected candidate failure'):'Not evaluated for this request';};
    display('legacy',result.legacy,result.servedBy==='legacy'||mode==='SHADOW');
    display('modern',mode==='LEGACY'||mode==='CANARY'&&!candidate?null:result.modern,mode!=='LEGACY'&&(mode!=='CANARY'||candidate));
    if(mode==='LEGACY'||mode==='CANARY'&&!candidate)$('modern-total').textContent='Bypassed';
    if(result.servedBy==='modern'){$('legacy-total').textContent='Standby';$('legacy-breakdown').textContent='Not evaluated unless candidate fails';}
    $('legacy-authority').textContent=result.servedBy==='legacy'?'SERVES RESPONSE':'FALLBACK READY';
    $('modern-authority').textContent=mode==='SHADOW'?'SHADOW COMPARE':candidate?'CANDIDATE':'BYPASSED';
    let title='Baseline serves.',copy='The legacy result is authoritative. The candidate is bypassed.';
    if(mode==='SHADOW'){
      title=result.failed?'Candidate failed safely.':result.mismatch?'Compatibility mismatch detected.':'Contract preserved.';
      copy=result.failed?'Shadow evaluation failed; the legacy response is unchanged.':result.mismatch?`Legacy returns ${money(result.legacy.total)}; candidate returns ${money(result.modern.total)}. Investigate before promotion.`:'Both implementations agree. The legacy result remains authoritative.';
    }else if(candidate){title=result.fallback?'Fallback to legacy.':'Candidate serves.';copy=result.fallback?'The read-only candidate threw an exception. The legacy adapter supplies the response.':`The modern result ${money(result.result.total)} is authoritative. This mode does not automatically compare business behavior.`;}
    $('outcome-title').textContent=title;$('outcome-copy').textContent=copy;$('outcome').classList.toggle('warning',result.mismatch||result.failed);$('outcome').querySelector('.outcome-icon').textContent=result.mismatch||result.failed?'!':'✓';
    $('cohort').replaceChildren(...Array.from({length:100},(_,i)=>{const cell=document.createElement('span');cell.className=(mode==='MODERN'||mode==='CANARY'&&i<percent?'candidate':'')+(i===cohort?' current':'');return cell;}));
  }catch(e){if(id===generation){$('error').textContent=e.message;$('error').hidden=false;}}
}
$('run').addEventListener('click',evaluate);$('percent').addEventListener('input',()=>{$('percent-label').textContent=$('percent').value+'%';evaluate();});
$('rollback').addEventListener('click',()=>{mode='LEGACY';syncMode();evaluate();});
for(const field of ['amount','region','member','routing-key','regression','failure'])$(field).addEventListener('change',evaluate);
syncMode();evaluate();
